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
        
        // Calculate each triggered rule
        for (CalculationRuleDto rule : triggeredRules) {
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
            log.debug("Executing calculation rule: {}", rule.getOutputSymbol());
            
            if (aggregatedRates == null || aggregatedRates.isEmpty()) {
                log.warn("No aggregated rates available for calculation: {}", rule.getOutputSymbol());
                return false;
            }
            
            // Use the utility for average calculation
            Optional<RateCalculationUtils.AverageResult> avgResult = 
                    RateCalculationUtils.calculateAverage(aggregatedRates);
                    
            if (avgResult.isEmpty()) {
                log.warn("Could not calculate rate: {}. No valid inputs available.", rule.getOutputSymbol());
                return false;
            }
            
            // Create the rate DTO from the result
            BaseRateDto calculatedRate = RateCalculationUtils.createAverageRate(
                    rule.getOutputSymbol(), 
                    avgResult.get(), 
                    "AVERAGE");
            
            // Cache the calculated rate
            rateCacheService.cacheCalculatedRate(calculatedRate);
            
            // Publish to Kafka
            if (sequentialPublisher != null) {
                sequentialPublisher.publishRate(calculatedRate);
            } else {
                log.warn("Publisher not available, rate not published: {}", rule.getOutputSymbol());
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
     * Check if a symbol is a cross rate (like EUR/TRY or GBP/TRY)
     */
    private boolean isCrossRate(String symbol) {
        if (symbol == null) return false;
        
        String normalized = symbol.toUpperCase().replace("/", "");
        return normalized.contains("TRY") && 
              (normalized.contains("EUR") || normalized.contains("GBP"));
    }
}
