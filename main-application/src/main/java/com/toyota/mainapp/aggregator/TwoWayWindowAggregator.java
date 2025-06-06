package com.toyota.mainapp.aggregator;

import com.toyota.mainapp.calculator.RateCalculatorService;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.calculator.RuleEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


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

    private final Map<String, List<String>> expectedProvidersConfig = new ConcurrentHashMap<>();

    @Value("${app.aggregator.max-time-skew-ms:${AGGREGATOR_MAX_TIME_SKEW_MS:3000}}")
    private long maxTimeSkewMs;

    private final Map<String, Map<String, BaseRateDto>> window = new ConcurrentHashMap<>();
    
    @Value("${app.aggregator.window-cleanup-interval-ms:${AGGREGATOR_POLL_INTERVAL_MS:60000}}")
    private long windowCleanupIntervalMs;

    @Value("${app.aggregator.window-timeout-ms:${AGGREGATOR_WINDOW_TIMEOUT_MS:800}}")
    private long windowTimeoutMs;

    @PostConstruct
    public void initializeDefaultConfig() {
        // Load environment values
        maxTimeSkewMs = getEnvLong("AGGREGATOR_MAX_TIME_SKEW_MS", maxTimeSkewMs);
        windowCleanupIntervalMs = getEnvLong("AGGREGATOR_POLL_INTERVAL_MS", windowCleanupIntervalMs);
        windowTimeoutMs = getEnvLong("AGGREGATOR_WINDOW_TIMEOUT_MS", 800L);

        List<String> usdTryProviders = List.of("RESTProvider1", "TCPProvider2");
        expectedProvidersConfig.put("USDTRY", usdTryProviders);
        
        List<String> eurUsdProviders = List.of("RESTProvider1", "TCPProvider2");
        expectedProvidersConfig.put("EURUSD", eurUsdProviders);
        
        List<String> gbpUsdProviders = List.of("RESTProvider1", "TCPProvider2"); 
        expectedProvidersConfig.put("GBPUSD", gbpUsdProviders);
        
        log.info("TwoWayWindowAggregator configured - maxTimeSkew: {}ms, cleanupInterval: {}ms, windowTimeout: {}ms", 
                maxTimeSkewMs, windowCleanupIntervalMs, windowTimeoutMs);

        taskScheduler.scheduleAtFixedRate(
            this::cleanupStaleWindows,
            Duration.ofMillis(windowCleanupIntervalMs)
        );
        log.info("Window cleanup scheduled every {} ms", windowCleanupIntervalMs);
    }

    public void accept(BaseRateDto baseRateDto) {
        // Skip invalid or non-raw rates
        if (baseRateDto == null || baseRateDto.getRateType() != RateType.RAW) {
            log.debug("Skipping non-raw or null rate: {}", 
                      baseRateDto != null ? baseRateDto.getRateType() : "null");
            return;
        }
        
        String providerName = baseRateDto.getProviderName();
        String baseSymbol = deriveBaseSymbol(baseRateDto.getSymbol());
        
        Map<String, BaseRateDto> symbolBucket = window.computeIfAbsent(baseSymbol, k -> new ConcurrentHashMap<>());
        symbolBucket.put(providerName, baseRateDto);
        
        List<String> expectedProviders = getExpectedProviders(baseSymbol);
        int collectedCount = symbolBucket.size();
        
        log.info("Window update: {} [{}/{}] providers - need: {}", 
                baseSymbol, collectedCount, expectedProviders.size(), 
                missingProviders(symbolBucket.keySet(), expectedProviders));

        if (collectedCount >= expectedProviders.size() && 
            symbolBucket.keySet().containsAll(expectedProviders)) {
            checkTimeSkewAndCalculate(baseSymbol, symbolBucket, expectedProviders);
        }
    }

    private String missingProviders(Set<String> collected, List<String> expected) {
        return expected.stream()
            .filter(provider -> !collected.contains(provider))
            .collect(Collectors.joining(", "));
    }

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

        if (relevantRates.size() == expectedProviders.size() && 
            isTimeSkewAcceptable(relevantRates, expectedProviders)) {

            Map<String, BaseRateDto> ratesForCalculation = relevantRates.values().stream()
                .collect(Collectors.toMap(BaseRateDto::getSymbol, this::cloneRateDto));

            triggerCalculation(ratesForCalculation);
            relevantRates.values().forEach(r -> r.setLastCalculationTimestamp(System.currentTimeMillis()));
            
            log.info("Calculation triggered for {} with {} rates", baseSymbol, relevantRates.size());
        } else if (relevantRates.size() < expectedProviders.size()) {
            log.debug("Missing expected providers for {}", baseSymbol);
        } else {
            log.warn("Time skew too high for {}, calculation skipped", baseSymbol);
        }
    }

    private BaseRateDto cloneRateDto(BaseRateDto original) {
        BaseRateDto cloned = BaseRateDto.builder()
            .symbol(original.getSymbol())
            .bid(original.getBid())
            .ask(original.getAsk())
            .timestamp(original.getTimestamp())
            .providerName(original.getProviderName())
            .rateType(original.getRateType())
            .build();

        if (original.getCalculationInputs() != null) {
            cloned.setCalculationInputs(new ArrayList<>(original.getCalculationInputs()));
        }
        
        return cloned;
    }

    private void cleanupStaleWindows() {
        try {
            log.debug("Eski pencere verileri temizleniyor...");
            long currentTime = System.currentTimeMillis();
            long staleCutoffTime = currentTime - (maxTimeSkewMs * 10);
            
            int cleanedSymbols = 0;
            int cleanedRates = 0;
            
            for (Map.Entry<String, Map<String, BaseRateDto>> entry : window.entrySet()) {
                String symbol = entry.getKey();
                Map<String, BaseRateDto> providers = entry.getValue();
                
                // Eski kurları temizle
                for (Iterator<Map.Entry<String, BaseRateDto>> it = providers.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, BaseRateDto> providerEntry = it.next();
                    BaseRateDto rate = providerEntry.getValue();

                    Long lastCalcTime = rate.getLastCalculationTimestamp();
                    boolean isStaleByTimestamp = rate.getTimestamp() == null || rate.getTimestamp() < staleCutoffTime;
                    boolean isStaleByCalcTime = lastCalcTime != null && lastCalcTime < staleCutoffTime;
                    
                    if (isStaleByTimestamp || isStaleByCalcTime) {
                        it.remove();
                        cleanedRates++;
                        log.debug("{} sembolü için {} sağlayıcısından eski kur temizlendi", 
                                 symbol, providerEntry.getKey());
                    }
                }

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
        
        long timeSkew = maxTimestamp - minTimestamp;
        boolean acceptable = timeSkew <= maxTimeSkewMs;
        
        log.debug("Kurlar için zaman kayması: {} ms, kabul edilebilir: {}", timeSkew, acceptable);
        return acceptable;
    }
    
    // değiştirebilirim 2 yerde var
    private void triggerCalculation(Map<String, BaseRateDto> rates) {
         try {
             Set<String> baseSymbols = rates.values().stream()
                .map(rate -> deriveBaseSymbol(rate.getSymbol()))
                .collect(Collectors.toSet());
        
        boolean hasCrossRatePotential = baseSymbols.stream()
                .anyMatch(s -> s.contains("USD") || s.contains("EUR") || s.contains("GBP"));
        
        if (hasCrossRatePotential) {
            log.info("Processing potential cross rate data for: {}", String.join(", ", baseSymbols));
        }
        
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

        rateCalculatorService.processWindowCompletion(rates);
        
    } catch (Exception e) {
        log.error("Calculation error: {}", e.getMessage(), e);
    }
}

    private String deriveBaseSymbol(String providerSymbol) {
        return com.toyota.mainapp.util.SymbolUtils.deriveBaseSymbol(providerSymbol);
    }

    private List<String> getExpectedProviders(String baseSymbol) {
        return expectedProvidersConfig.getOrDefault(baseSymbol, Collections.emptyList());
    }

    public void initialize(Map<String, List<String>> symbolProvidersConfig) {
        if (symbolProvidersConfig != null) {
            this.expectedProvidersConfig.putAll(symbolProvidersConfig);
            log.info("Toplayıcı {} sembol yapılandırması ile başlatıldı", symbolProvidersConfig.size());
        }
    }

    private long getEnvLong(String envName, long defaultValue) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            try {
                return Long.parseLong(envValue.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid long value for {}: {}, using default: {}", envName, envValue, defaultValue);
            }
        }
        return defaultValue;
    }
}
