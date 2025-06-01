package com.toyota.mainapp.aggregator;

import com.toyota.mainapp.calculator.RateCalculatorService;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.util.SymbolUtils;

import com.toyota.mainapp.calculator.RuleEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * İki yönlü pencere bazlı kur verisi toplayıcı.
 */
@Component
@Slf4j
public class TwoWayWindowAggregator {
    
    private final RateCalculatorService rateCalculatorService;
    private final RuleEngineService ruleEngineService;
    private final TaskScheduler taskScheduler;

    public TwoWayWindowAggregator(
            TaskScheduler taskScheduler,
            RateCalculatorService rateCalculatorService,
            RuleEngineService ruleEngineService) {
        this.taskScheduler = taskScheduler;
        this.rateCalculatorService = rateCalculatorService;
        this.ruleEngineService = ruleEngineService;
        log.info("TwoWayWindowAggregator initialized");
    }
    
    // Konfigürasyondan beklenen sağlayıcılar bilgisi alınır
    private final Map<String, List<String>> expectedProvidersConfig = new ConcurrentHashMap<>();
    
    // Zaman penceresi konfigürasyonu (milisaniye cinsinden)
    @Value("${app.aggregator.max-time-skew-ms:3000}")
    private long maxTimeSkewMs;
    
    // İç pencere yapısı: baseSymbol -> (providerName -> BaseRateDto)
    private final Map<String, Map<String, BaseRateDto>> window = new ConcurrentHashMap<>();
    
    @Value("${app.aggregator.window-cleanup-interval-ms:60000}")
    private long windowCleanupIntervalMs;
    
    /**
     * Initialize with default provider configuration and start scheduled tasks
     */
    @PostConstruct
    public void initializeDefaultConfig() {
        // Initialize with provider names that match what you're actually using
        List<String> usdTryProviders = List.of("RESTProvider1", "TCPProvider2");
        expectedProvidersConfig.put("USDTRY", usdTryProviders);
        
        List<String> eurUsdProviders = List.of("RESTProvider1", "TCPProvider2");
        expectedProvidersConfig.put("EURUSD", eurUsdProviders);
        
        List<String> gbpUsdProviders = List.of("RESTProvider1", "TCPProvider2"); 
        expectedProvidersConfig.put("GBPUSD", gbpUsdProviders);
        
        log.info("TwoWayWindowAggregator initialized with default configuration for currencies with providers: {}", 
                String.join(", ", usdTryProviders));
        
        // Schedule periodic cleanup using Spring's TaskScheduler
        taskScheduler.scheduleAtFixedRate(
            this::cleanupStaleWindows,
            Duration.ofMillis(windowCleanupIntervalMs)
        );
        log.info("Window cleanup scheduled every {} ms", windowCleanupIntervalMs);
    }

    /**
     * Accept a new rate and process it through the aggregator
     */
    public void accept(BaseRateDto baseRateDto) {
        // Skip invalid or non-raw rates
        if (baseRateDto == null || baseRateDto.getRateType() != RateType.RAW) {
            log.debug("Skipping non-raw or null rate: {}", 
                      baseRateDto != null ? baseRateDto.getRateType() : "null");
            return;
        }
        
        String providerName = baseRateDto.getProviderName();
        String baseSymbol = deriveBaseSymbol(baseRateDto.getSymbol());
        
        // Store rate in window and check for calculation readiness
        Map<String, BaseRateDto> symbolBucket = window.computeIfAbsent(baseSymbol, k -> new ConcurrentHashMap<>());
        symbolBucket.put(providerName, baseRateDto);
        
        // Check if we have all expected providers
        List<String> expectedProviders = getExpectedProviders(baseSymbol);
        int collectedCount = symbolBucket.size();
        
        log.info("Window update: {} [{}/{}] providers - need: {}", 
                baseSymbol, collectedCount, expectedProviders.size(), 
                missingProviders(symbolBucket.keySet(), expectedProviders));
        
        // Try to calculate if window looks complete
        if (collectedCount >= expectedProviders.size() && 
            symbolBucket.keySet().containsAll(expectedProviders)) {
            checkTimeSkewAndCalculate(baseSymbol, symbolBucket, expectedProviders);
        }
    }

    /**
     * Returns a string of providers that are still missing
     */
    private String missingProviders(Set<String> collected, List<String> expected) {
        return expected.stream()
            .filter(provider -> !collected.contains(provider))
            .collect(Collectors.joining(", "));
    }

    /**
     * Verify time skew constraints and trigger calculation if valid
     */
    private void checkTimeSkewAndCalculate(String baseSymbol, Map<String, BaseRateDto> symbolBucket, 
                                          List<String> expectedProviders) {
        // Extract only rates from expected providers
        Map<String, BaseRateDto> relevantRates = new HashMap<>();
        for (String provider : expectedProviders) {
            BaseRateDto rate = symbolBucket.get(provider);
            if (rate != null) {
                relevantRates.put(provider, rate);
            }
        }
        
        // Only calculate if all expected providers present and time skew is acceptable
        if (relevantRates.size() == expectedProviders.size() && 
            isTimeSkewAcceptable(relevantRates, expectedProviders)) {
            
            // Prepare rates for calculation (clone to avoid mutations)
            Map<String, BaseRateDto> ratesForCalculation = relevantRates.values().stream()
                .collect(Collectors.toMap(BaseRateDto::getSymbol, this::cloneRateDto));
            
            // Trigger calculation and mark rates as processed
            triggerCalculation(ratesForCalculation);
            relevantRates.values().forEach(r -> r.setLastCalculationTimestamp(System.currentTimeMillis()));
            
            log.info("Calculation triggered for {} with {} rates", baseSymbol, relevantRates.size());
        } else if (relevantRates.size() < expectedProviders.size()) {
            log.debug("Missing expected providers for {}", baseSymbol);
        } else {
            log.warn("Time skew too high for {}, calculation skipped", baseSymbol);
        }
    }
    
    /**
     * Create a clone of a BaseRateDto to avoid mutating window data
     */
    private BaseRateDto cloneRateDto(BaseRateDto original) {
        // Create a basic clone with essential fields
        BaseRateDto cloned = BaseRateDto.builder()
            .symbol(original.getSymbol())
            .bid(original.getBid())
            .ask(original.getAsk())
            .timestamp(original.getTimestamp())
            .providerName(original.getProviderName())
            .rateType(original.getRateType())
            .build();
            
        // Copy other important fields if present
        if (original.getCalculationInputs() != null) {
            cloned.setCalculationInputs(new ArrayList<>(original.getCalculationInputs()));
        }
        
        return cloned;
    }
    
    /**
     * Eski pencere verilerini temizle - zamanla oluşabilecek hafıza sızıntılarını önler
     */
    private void cleanupStaleWindows() {
        try {
            log.debug("Eski pencere verileri temizleniyor...");
            long currentTime = System.currentTimeMillis();
            long staleCutoffTime = currentTime - (maxTimeSkewMs * 10); // 10 kat daha uzun bir pencere kullanımı
            
            int cleanedSymbols = 0;
            int cleanedRates = 0;
            
            for (Map.Entry<String, Map<String, BaseRateDto>> entry : window.entrySet()) {
                String symbol = entry.getKey();
                Map<String, BaseRateDto> providers = entry.getValue();
                
                // Eski kurları temizle
                for (Iterator<Map.Entry<String, BaseRateDto>> it = providers.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, BaseRateDto> providerEntry = it.next();
                    BaseRateDto rate = providerEntry.getValue();
                    
                    // Check both original timestamp and last calculation timestamp (if it exists)
                    Long lastCalcTime = rate.getLastCalculationTimestamp();
                    boolean isStaleByTimestamp = rate.getTimestamp() == null || rate.getTimestamp() < staleCutoffTime;
                    boolean isStaleByCalcTime = lastCalcTime != null && lastCalcTime < staleCutoffTime;
                    
                    // Zaman damgası null veya çok eski olan kurları ve ayrıca son hesaplama zamanı çok eski olanları temizle
                    if (isStaleByTimestamp || isStaleByCalcTime) {
                        it.remove();
                        cleanedRates++;
                        log.debug("{} sembolü için {} sağlayıcısından eski kur temizlendi", 
                                 symbol, providerEntry.getKey());
                    }
                }
                
                // Boş kalan sembol sepetiyle istemiyoruz, temizle
                if (providers.isEmpty()) {
                    window.remove(symbol);
                    cleanedSymbols++;
                    log.debug("{} sembolü için boş pencere temizlendi", symbol);
                }
            }
            
            if (cleanedRates > 0 || cleanedSymbols > 0) {
                log.info("Pencere temizleme tamamlandı: {} sembol ve {} kur temizlendi", 
                        cleanedSymbols, cleanedRates);
            }
        } catch (Exception e) {
            log.error("Pencere temizleme sırasında hata oluştu", e);
        }
    }
    
    /**
     * Kurlar arasındaki zaman kaymasının kabul edilebilir sınırlar içinde olup olmadığını kontrol eder
     */
    private boolean isTimeSkewAcceptable(Map<String, BaseRateDto> symbolBucket, List<String> providers) {
        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = Long.MIN_VALUE;
        
        // Min ve max zaman damgalarını bul
        for (String provider : providers) {
            BaseRateDto rate = symbolBucket.get(provider);
            if (rate != null && rate.getTimestamp() != null) {
                long timestamp = rate.getTimestamp();
                minTimestamp = Math.min(minTimestamp, timestamp);
                maxTimestamp = Math.max(maxTimestamp, timestamp);
            }
        }
        
        // Zaman kaymasını hesapla ve kabul edilebilir mi kontrol et
        long timeSkew = maxTimestamp - minTimestamp;
        boolean acceptable = timeSkew <= maxTimeSkewMs;
        
        log.debug("Kurlar için zaman kayması: {} ms, kabul edilebilir: {}", timeSkew, acceptable);
        return acceptable;
    }
    
    /**
     * Toplanan kurlar için hesaplamayı tetikle
     */
    // TwoWayWindowAggregator içinde eklenecek veya değiştirilecek bölüm
private void triggerCalculation(Map<String, BaseRateDto> rates) {
    try {
        // Extract base symbols for diagnostics
        Set<String> baseSymbols = rates.values().stream()
                .map(rate -> deriveBaseSymbol(rate.getSymbol()))
                .collect(Collectors.toSet());
        
        // Check for cross rate candidates
        boolean hasCrossRatePotential = baseSymbols.stream()
                .anyMatch(s -> s.contains("USD") || s.contains("EUR") || s.contains("GBP"));
        
        if (hasCrossRatePotential) {
            log.info("Processing potential cross rate data for: {}", String.join(", ", baseSymbols));
        }
        
        // Verify rule availability
        int ruleCount = baseSymbols.stream()
                .mapToInt(s -> {
                    List<CalculationRuleDto> rules = ruleEngineService.getRulesByInputBaseSymbol(s);
                    return rules != null ? rules.size() : 0;
                })
                .sum();
                
        if (ruleCount == 0) {
            log.warn("No calculation rules found for symbols: {}", String.join(", ", baseSymbols));
            return;
        }
        
        // Proceed with calculation
        rateCalculatorService.processWindowCompletion(rates);
        
    } catch (Exception e) {
        log.error("Calculation error: {}", e.getMessage(), e);
    }
}
    
    /**
     * Sağlayıcıya özgü sembolden temel sembolü türet
     */
    private String deriveBaseSymbol(String providerSymbol) {
        return com.toyota.mainapp.util.SymbolUtils.deriveBaseSymbol(providerSymbol);
    }
    /**
     * Bir temel sembol için beklenen sağlayıcıların listesini al
     */
    private List<String> getExpectedProviders(String baseSymbol) {
        return expectedProvidersConfig.getOrDefault(baseSymbol, Collections.emptyList());
    }
    
    /**
     * Yapılandırma ile başlatma
     */
    public void initialize(Map<String, List<String>> symbolProvidersConfig) {
        if (symbolProvidersConfig != null) {
            this.expectedProvidersConfig.putAll(symbolProvidersConfig);
            log.info("Toplayıcı {} sembol yapılandırması ile başlatıldı", symbolProvidersConfig.size());
        }
    }
}
