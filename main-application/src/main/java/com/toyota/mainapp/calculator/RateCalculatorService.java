package com.toyota.mainapp.calculator;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import com.toyota.mainapp.dto.RateStatusDto;
import com.toyota.mainapp.dto.RawRateDto;
import com.toyota.mainapp.mapper.RateMapper;
import com.toyota.mainapp.kafka.producer.KafkaRateProducer;
import com.toyota.mainapp.kafka.producer.SimpleFormatKafkaProducer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for calculating derived rates from raw rates
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateCalculatorService {

    private final RateCacheService rateCacheService;
    private final RuleEngineService ruleEngineService;
    private final RateMapper rateMapper;
    private final KafkaRateProducer kafkaRateProducer;
    private final SimpleFormatKafkaProducer simpleFormatProducer;
    private final List<String> expectedProviders = Arrays.asList("TCPProvider2", "RESTProvider1");
    /**
     * Process rate update (either raw rate or calculated rate)
     */
    @CircuitBreaker(name = "calculatorService")
    @Retry(name = "calculatorRetry")
    public void processRateUpdate(String cacheKey, boolean isRawRate) {
        try {
            log.info("Processing rate update for key: {}, isRawRate: {}", cacheKey, isRawRate);
            if (isRawRate) {
                processRawRateUpdate(cacheKey);
            } else {
                log.debug("Hesaplanmış kur güncellemesi algılandı, anahtar: {}", cacheKey);
            }
        } catch (Exception e) {
            log.error("Kur güncellemesi işlenirken hata oluştu, anahtar: {}", cacheKey, e);
            throw e;
        }
    }
    
    /**
     * Process raw rate update and calculate dependent rates
     */
    private void processRawRateUpdate(String rawRateCacheKey) {
        log.info("Processing raw rate update for key: {}", rawRateCacheKey);
        rateCacheService.getRawRate(rawRateCacheKey).ifPresentOrElse(
            updatedRate -> {
                // Debug log for type inspection
                log.debug("Fetched rate from cache, type: {}, content: {}", 
                         updatedRate != null ? updatedRate.getClass().getName() : "null", 
                         updatedRate);
                
                // Log rate status
                RateStatusDto rateStatus = rateMapper.toRateStatusDto(updatedRate);
                log.info("Hesaplama için kur durumu: {}", rateStatus);
                
                // Get and process rules dependent on this symbol
                List<CalculationRuleDto> dependentRules = ruleEngineService.getRulesByInputSymbol(updatedRate.getSymbol());
                log.debug("Found {} dependent rules for symbol: {}", dependentRules.size(), updatedRate.getSymbol());
                dependentRules.forEach(this::calculateAndCacheRate);
            },
            () -> log.warn("Önbellekte kur bulunamadı, anahtar: {}", rawRateCacheKey)
        );
    }
    
    /**
     * Calculate rate using the specified rule and cache the result
     */
    private void calculateAndCacheRate(CalculationRuleDto rule) {
        try {
            // Get all input rates needed for calculation
            Map<String, RawRateDto> inputRates = collectInputRates(rule);
            
            // Validate we have all required inputs
            String[] requiredSymbols = rule.getDependsOnRaw();
            if (requiredSymbols == null || requiredSymbols.length == 0) {
                log.warn("Kural için bağımlı ham kurlar tanımlanmamış: {}", rule.getOutputSymbol());
                return;
            }
            
            if (inputRates.size() < requiredSymbols.length) {
                logMissingInputs(rule, inputRates, requiredSymbols);
                return;
            }
            
            // Execute calculation
            CalculatedRateDto calculatedRate = ruleEngineService.executeRule(rule, inputRates);
            
            if (calculatedRate != null) {
                // Cache the calculated rate
                rateCacheService.cacheCalculatedRate(calculatedRate);
                
                // Send to Kafka - JSON format
                kafkaRateProducer.sendCalculatedRate(rateMapper.toCalculatedRatePayloadDto(calculatedRate));
                
                // Send to Kafka - Simple text format
                simpleFormatProducer.sendCalculatedRate(calculatedRate);
                
                log.info("Hesaplanmış kur önbelleğe alındı ve Kafka'ya gönderildi: {}", 
                        calculatedRate.getSymbol());
            }
        } catch (Exception e) {
            // Create error status DTO
            RateStatusDto errorStatus = rateMapper.createRateStatusDto(
                rule.getOutputSymbol(),
                "CALCULATOR",
                RateStatusDto.RateStatusEnum.ERROR,
                e.getMessage()
            );
            
            log.error("Kur hesaplaması sırasında hata oluştu: {}", errorStatus, e);
        }
    }
    
    /**
     * Log detailed information about missing inputs
     */
    private void logMissingInputs(CalculationRuleDto rule, Map<String, RawRateDto> inputRates, String[] requiredSymbols) {
        StringBuilder missingSymbols = new StringBuilder();
        for (String symbol : requiredSymbols) {
            if (!inputRates.containsKey(symbol)) {
                if (missingSymbols.length() > 0) {
                    missingSymbols.append(", ");
                }
                missingSymbols.append(symbol);
            }
        }
        
        RateStatusDto errorStatus = rateMapper.createRateStatusDto(
            rule.getOutputSymbol(),
            "CALCULATOR",
            RateStatusDto.RateStatusEnum.ERROR,
            "Hesaplama için gerekli giriş kurları eksik: " + missingSymbols
        );
        
        log.warn("Hesaplama hatası: {}", errorStatus);
    }
    
    /**
     * Collect all input rates required for a calculation rule
     */
    private Map<String, RawRateDto> collectInputRates(CalculationRuleDto rule) {
    Map<String, RawRateDto> result = new HashMap<>();
    // Try both without and with provider prefix when looking up rates
    String[] requiredSymbols = rule.getDependsOnRaw(); // Use getDependsOnRaw instead of getRequiredInputs
    if (requiredSymbols == null) {
        log.warn("Rule {} has no required inputs defined", rule.getOutputSymbol());
        return result;
    }
    
    for (String inputSymbol : requiredSymbols) {
        // Try first without provider prefix (original approach)
        Optional<RawRateDto> rate = rateCacheService.getRawRate(inputSymbol);
        
        // If not found, try with provider prefixes
        if (rate.isEmpty()) {
            for (String provider : expectedProviders) {
                String prefixedKey = provider + "_" + inputSymbol;
                log.debug("Looking for rate with key: {}", prefixedKey);
                rate = rateCacheService.getRawRate(prefixedKey);
                if (rate.isPresent()) {
                    log.debug("Found rate with key: {}", prefixedKey);
                    break;
                }
            }
        }
        
        if (rate.isPresent()) {
            result.put(inputSymbol, rate.get());
        } else {
            log.warn("Rate not found in cache for symbol: {}", inputSymbol);
        }
    }
    return result;
    }
    
    /**
     * Pencere toplayıcısından gelen toplanmış verilerle hesaplama yap.
     * Bu metot, TwoWayWindowAggregator tarafından tüm beklenen sağlayıcılar
     * bir zaman penceresi içinde veri gönderdiğinde çağrılır.
     * 
     * @param baseSymbol Temel sembol (örn. "USDTRY")
     * @param aggregatedRates Farklı sağlayıcılardan toplanan ham kurlar
     */
    public void calculateFromAggregatedData(String baseSymbol, Map<String, RawRateDto> aggregatedRates) {
        log.info("{} için toplanmış verilerden hesaplama yapılıyor, {} kur mevcut", 
                baseSymbol, aggregatedRates.size());
        
        try {
            // Bu temel sembol için ilgili hesaplama kurallarını bul
            List<CalculationRuleDto> rules = ruleEngineService.getRulesByInputBaseSymbol(baseSymbol);
            
            if (rules.isEmpty()) {
                log.warn("{} temel sembolü için hesaplama kuralı bulunamadı", baseSymbol);
                return;
            }
            
            // Her kuralı çalıştır
            for (CalculationRuleDto rule : rules) {
                executeRuleWithAggregatedData(rule, aggregatedRates);
            }
        } catch (Exception e) {
            log.error("{} için toplanmış verilerden hesaplama yapılırken hata: {}", 
                    baseSymbol, e.getMessage(), e);
        }
    }
    
    /**
     * Toplanmış verileri kullanarak belirli bir kuralı çalıştır
     */
    private void executeRuleWithAggregatedData(CalculationRuleDto rule, Map<String, RawRateDto> aggregatedRates) {
        try {
            // Kural motoru için beklenen arayüze uygun bir harita oluştur
            Map<String, RawRateDto> inputRates = new HashMap<>();
            
            // Sağlayıcı kurlarını kural motorunun beklediği formata dönüştür
            for (Map.Entry<String, RawRateDto> entry : aggregatedRates.entrySet()) {
                String provider = entry.getKey();
                RawRateDto rate = entry.getValue();
                
                // Anahtar biçimi, kural motorunuzun beklentisine bağlı olabilir
                // Sadece sembol veya sağlayıcı_sembol olabilir
                String ruleInputKey = rate.getSymbol(); // Ya da: provider + "_" + rate.getSymbol()
                inputRates.put(ruleInputKey, rate);
            }
            
            // Kuralı çalıştır
            CalculatedRateDto calculatedRate = ruleEngineService.executeRule(rule, inputRates);
            
            if (calculatedRate != null) {
                // Hesaplanan kuru önbelleğe al
                rateCacheService.cacheCalculatedRate(calculatedRate);
                
                // Kafka'ya gönder - JSON formatı
                kafkaRateProducer.sendCalculatedRate(rateMapper.toCalculatedRatePayloadDto(calculatedRate));
                
                // Kafka'ya gönder - Basit metin formatı
                simpleFormatProducer.sendCalculatedRate(calculatedRate);
                
                log.info("Toplanmış verilerden hesaplanan kur: {}", calculatedRate.getSymbol());
            } else {
                log.warn("{} için toplanmış verilerden hesaplama başarısız oldu", rule.getOutputSymbol());
            }
        } catch (Exception e) {
            log.error("{} için toplanmış verilerle kural çalıştırılırken hata: {}", 
                    rule.getOutputSymbol(), e.getMessage(), e);
        }
    }
}
