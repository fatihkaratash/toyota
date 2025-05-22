package com.toyota.mainapp.calculator;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import com.toyota.mainapp.dto.RateStatusDto;
import com.toyota.mainapp.dto.RawRateDto;
import com.toyota.mainapp.mapper.RateMapper;
import com.toyota.mainapp.kafka.producer.KafkaRateProducer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
    
    /**
     * Process rate update (either raw rate or calculated rate)
     */
    @CircuitBreaker(name = "calculatorService")
    @Retry(name = "calculatorRetry")
    public void processRateUpdate(String cacheKey, boolean isRawRate) {
        try {
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
        rateCacheService.getRawRate(rawRateCacheKey).ifPresentOrElse(
            updatedRate -> {
                // Log rate status
                RateStatusDto rateStatus = rateMapper.toRateStatusDto(updatedRate);
                log.info("Hesaplama için kur durumu: {}", rateStatus);
                
                // Get and process rules dependent on this symbol
                List<CalculationRuleDto> dependentRules = ruleEngineService.getRulesByInputSymbol(updatedRate.getSymbol());
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
                
                // Send to Kafka - should be added to complete the flow
                kafkaRateProducer.sendCalculatedRate(rateMapper.toCalculatedRatePayloadDto(calculatedRate));
                
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
        Map<String, RawRateDto> inputRates = new ConcurrentHashMap<>();
        
        String[] dependentSymbols = rule.getDependsOnRaw();
        if (dependentSymbols == null || dependentSymbols.length == 0) {
            return inputRates;
        }
        
        for (String symbol : dependentSymbols) {
            rateCacheService.getRawRate(symbol).ifPresent(rawRate -> 
                inputRates.put(symbol, rawRate)
            );
        }
        
        return inputRates;
    }
}
