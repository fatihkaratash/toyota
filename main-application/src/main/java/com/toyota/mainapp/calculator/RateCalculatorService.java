package com.toyota.mainapp.calculator;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.dependency.RateDependencyManager;
import com.toyota.mainapp.calculator.collector.CrossRateCollector;
import com.toyota.mainapp.calculator.collector.CrossRateCalculationCallback;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.dto.model.InputRateInfo;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.mapper.RateMapper;
import com.toyota.mainapp.util.SymbolUtils;
import com.toyota.mainapp.util.RateCalculationUtils;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct; // Changed from javax to jakarta

/**
 * Service for calculating derived rates from raw rates
 */
@Service
@Slf4j
public class RateCalculatorService implements CrossRateCalculationCallback {
    private final RateCacheService rateCacheService;
    private final RuleEngineService ruleEngineService;
    private final RateMapper rateMapper;
    private final RateDependencyManager rateDependencyManager; 
    
    // Break circular dependency with setter injection
    private KafkaPublishingService sequentialPublisher;
    
    // Use @Lazy to break circular dependency
    private final CrossRateCollector crossRateCollector;
    
    // Add a cache for calculation inputs to prevent redundant calculations
    private final Map<String, CalculationCache> calculationInputCache = new ConcurrentHashMap<>();
    private static final long CACHE_VALIDITY_MS = 5000; // 5 seconds
    
    public RateCalculatorService(
            RateCacheService rateCacheService,
            RuleEngineService ruleEngineService,
            RateMapper rateMapper,
            RateDependencyManager rateDependencyManager,
            @Lazy CrossRateCollector crossRateCollector) { 
        this.rateCacheService = rateCacheService;
        this.ruleEngineService = ruleEngineService;
        this.rateMapper = rateMapper;
        this.rateDependencyManager = rateDependencyManager;
        this.crossRateCollector = crossRateCollector;
        log.info("RateCalculatorService initialized");
    }
    
    // Helper class to store calculation input data for cache comparison
    private static class CalculationCache {
        private final Map<String, RateSnapshot> inputSnapshots;
        private final BaseRateDto result;
        private final long calculationTime;
        
        public CalculationCache(Map<String, BaseRateDto> inputs, BaseRateDto result) {
            this.inputSnapshots = new HashMap<>();
            for (Map.Entry<String, BaseRateDto> entry : inputs.entrySet()) {
                BaseRateDto rate = entry.getValue();
                if (rate != null && rate.getBid() != null && rate.getAsk() != null) {
                    inputSnapshots.put(entry.getKey(), new RateSnapshot(rate));
                }
            }
            this.result = result;
            this.calculationTime = System.currentTimeMillis();
        }
        
        public boolean isStillValid(Map<String, BaseRateDto> currentInputs, long currentTime) {
            // Check if cache is too old
            if (currentTime - calculationTime > CACHE_VALIDITY_MS) {
                return false;
            }
            
            // Check if all required inputs are present and match
            for (Map.Entry<String, RateSnapshot> entry : inputSnapshots.entrySet()) {
                String key = entry.getKey();
                RateSnapshot snapshot = entry.getValue();
                BaseRateDto currentRate = currentInputs.get(key);
                
                // If any input is missing or different, cache is invalid
                if (currentRate == null || !snapshot.matches(currentRate)) {
                    return false;
                }
            }
            
            // All inputs match, cache is valid
            return true;
        }
        
        public BaseRateDto getResult() {
            return result;
        }
    }
    
    // Helper class to store bid/ask/timestamp snapshots for comparison
    private static class RateSnapshot {
        private final BigDecimal bid;
        private final BigDecimal ask;
        private final Long timestamp;
        
        public RateSnapshot(BaseRateDto rate) {
            this.bid = rate.getBid();
            this.ask = rate.getAsk();
            this.timestamp = rate.getTimestamp();
        }
        
        public boolean matches(BaseRateDto rate) {
            if (rate == null || rate.getBid() == null || rate.getAsk() == null) {
                return false;
            }
            
            // Compare bid/ask values
            return bid.compareTo(rate.getBid()) == 0 && 
                   ask.compareTo(rate.getAsk()) == 0;
            // Note: we intentionally don't compare timestamps as they might be updated 
            // even when the actual values haven't changed
        }
    }
    
    @PostConstruct
    public void init() {
        // Register this service as the calculation callback with the collector
        if (crossRateCollector != null) {
            crossRateCollector.setCalculationCallback(this);
            log.info("RateCalculatorService registered as callback with CrossRateCollector");
        } else {
            log.warn("CrossRateCollector is null, callback not registered");
        }
    }
    @Autowired
    public void setSequentialPublisher(KafkaPublishingService sequentialPublisher) {
        this.sequentialPublisher = sequentialPublisher;
        log.info("SequentialPublisher set in RateCalculatorService");
    }
    
    /**
     * Processes a window of raw rates that have just been completed by the aggregator.
     * This method will find rules triggered by these raw rates and attempt to execute them.
     * @param newlyCompletedRawRatesInWindow A map of providerSpecificSymbol to BaseRateDto.
     */
    @CircuitBreaker(name = "calculatorService")
    @Retry(name = "calculatorRetry")
    public void processWindowCompletion(Map<String, BaseRateDto> newlyCompletedRawRatesInWindow) {
        if (newlyCompletedRawRatesInWindow == null || newlyCompletedRawRatesInWindow.isEmpty()) {
            log.warn("processWindowCompletion boş/null pencere ile çağrıldı");
            return;
        }
        
        log.info("Processing window completion with {} raw rates", newlyCompletedRawRatesInWindow.size());
        
        // Ensure all input rates are RAW type
        for (BaseRateDto rate : newlyCompletedRawRatesInWindow.values()) {
            if (rate.getRateType() != RateType.RAW) {
                log.warn("Fixing non-RAW rate type in window: {}", rate.getSymbol());
                rate.setRateType(RateType.RAW);
            }
        }

        // Group rates by base symbol
        Map<String, List<BaseRateDto>> ratesByBaseSymbol = groupRatesByBaseSymbol(newlyCompletedRawRatesInWindow);
        
        // Find rules to trigger
        Set<CalculationRuleDto> triggeredRules = findTriggeredRules(ratesByBaseSymbol.keySet());
        
        if (triggeredRules.isEmpty()) {
            log.info("No calculation rules triggered by the completed window");
            return;
        }
        
        // Calculate each triggered rule - use a Set to ensure uniqueness by output symbol
        Set<String> processedOutputSymbols = new HashSet<>();
        for (CalculationRuleDto rule : triggeredRules) {
            // Skip duplicate rules for the same output symbol
            if (!processedOutputSymbols.add(rule.getOutputSymbol())) {
                log.debug("Skipping duplicate rule for output symbol: {}", rule.getOutputSymbol());
                continue;
            }
            calculateRateFromRule(rule, newlyCompletedRawRatesInWindow);
        }

        // Process any pending cross rate calculations
        if (crossRateCollector != null) {
            crossRateCollector.processAllPendingChainCalculations();
        } 
    }
    
    /**
     * Groups rates by their base symbol
     */
    private Map<String, List<BaseRateDto>> groupRatesByBaseSymbol(Map<String, BaseRateDto> rates) {
        Map<String, List<BaseRateDto>> ratesByBaseSymbol = new HashMap<>();
        
        for (Map.Entry<String, BaseRateDto> entry : rates.entrySet()) {
            String providerSpecificSymbol = entry.getKey();
            BaseRateDto rate = entry.getValue();
            String baseSymbol = SymbolUtils.deriveBaseSymbol(providerSpecificSymbol);
            
            ratesByBaseSymbol
                .computeIfAbsent(baseSymbol, k -> new ArrayList<>())
                .add(rate);
        }
        
        return ratesByBaseSymbol;
    }
    
    /**
     * Find rules triggered by the given base symbols
     */
    private Set<CalculationRuleDto> findTriggeredRules(Set<String> baseSymbols) {
        Set<CalculationRuleDto> triggeredRules = new HashSet<>();
        
        for (String baseSymbol : baseSymbols) {
            List<CalculationRuleDto> rules = ruleEngineService.getRulesByInputBaseSymbol(baseSymbol);
            
            if (rules != null && !rules.isEmpty()) {
                log.info("Found {} calculation rules for base symbol {}", rules.size(), baseSymbol);
                triggeredRules.addAll(rules);
            }
        }
        
        return triggeredRules;
    }
    
    /**
     * Calculate a single rate using the provided rule and aggregated data
     * Now implements CrossRateCalculationCallback interface
     */
    @Override
    public boolean calculateRateFromRule(CalculationRuleDto rule, Map<String, BaseRateDto> aggregatedRates) {
        try {
            String outputSymbol = rule.getOutputSymbol();
            log.debug("Executing calculation rule: {}", outputSymbol);
            
            if (aggregatedRates == null || aggregatedRates.isEmpty()) {
                log.warn("No aggregated rates available for calculation: {}", outputSymbol);
                return false;
            }
            
            // Check if we recently calculated this rate with the same input values
            String cacheKey = generateCacheKey(outputSymbol, aggregatedRates);
            CalculationCache cachedCalc = calculationInputCache.get(cacheKey);
            long currentTime = System.currentTimeMillis();
            
            if (cachedCalc != null && cachedCalc.isStillValid(aggregatedRates, currentTime)) {
                BaseRateDto cachedResult = cachedCalc.getResult();
                log.info("Using cached calculation for {}: bid={}, ask={}, from {} ms ago", 
                        outputSymbol, cachedResult.getBid(), cachedResult.getAsk(),
                        currentTime - cachedCalc.calculationTime);
                
                // Update timestamp on the cached result
                cachedResult.setTimestamp(currentTime);
                
                // Still cache and publish the cached result to ensure consistent behavior
                rateCacheService.cacheCalculatedRate(cachedResult);
                if (sequentialPublisher != null) {
                    sequentialPublisher.publishRate(cachedResult);
                }
                
                return true;
            }
            
            // Use the utility for average calculation
            Optional<RateCalculationUtils.AverageResult> avgResult = 
                    RateCalculationUtils.calculateAverage(aggregatedRates);
                    
            if (avgResult.isEmpty()) {
                log.warn("Could not calculate rate: {}. No valid inputs available.", outputSymbol);
                return false;
            }
            
            // Create the rate DTO from the result
            BaseRateDto calculatedRate = RateCalculationUtils.createAverageRate(
                    outputSymbol, 
                    avgResult.get(), 
                    "AVERAGE");
            
            // Cache the calculated rate
            rateCacheService.cacheCalculatedRate(calculatedRate);
            
            // Store in our calculation cache for future reference
            calculationInputCache.put(cacheKey, new CalculationCache(aggregatedRates, calculatedRate));
            
            // Publish to Kafka
            if (sequentialPublisher != null) {
                sequentialPublisher.publishRate(calculatedRate);
            } else {
                log.warn("Publisher not available, rate not published: {}", outputSymbol);
            }
            
            log.info("Successfully calculated rate: {} using {} inputs", 
                    calculatedRate.getSymbol(), avgResult.get().validRateCount());
            
            // Process chain calculations if this is a base rate (not a cross rate)
            if (crossRateCollector != null && !RateCalculationUtils.isCrossRate(calculatedRate.getSymbol())) {
                crossRateCollector.processChainCalculationsForRate(calculatedRate);
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error calculating rate for rule {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Generate a cache key based on output symbol and input rates
     */
    private String generateCacheKey(String outputSymbol, Map<String, BaseRateDto> inputs) {
        StringBuilder keyBuilder = new StringBuilder(outputSymbol);
        
        // Add inputs to the key in a consistent order
        List<String> sortedKeys = new ArrayList<>(inputs.keySet());
        Collections.sort(sortedKeys);
        
        for (String key : sortedKeys) {
            BaseRateDto rate = inputs.get(key);
            if (rate != null && rate.getBid() != null && rate.getAsk() != null) {
                keyBuilder.append("_")
                         .append(key)
                         .append(":")
                         .append(rate.getBid())
                         .append(":")
                         .append(rate.getAsk());
            }
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * Check if a symbol is a cross rate (like EUR/TRY or GBP/TRY)
     */
    private boolean isCrossRate(String symbol) {
        if (symbol == null) return false;
        
        String normalized = symbol.toUpperCase().replace("/", "");
        return normalized.contains("TRY") && 
              (normalized.contains("EUR") || normalized.contains("GBP"));
    }
}
