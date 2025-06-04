package com.toyota.mainapp.calculator.collector;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.dependency.RateDependencyManager;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.util.SymbolUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
public class CrossRateCollector {

    private final RateCacheService rateCacheService;
    private final RateDependencyManager rateDependencyManager;
    
    // Callback for calculation - will be set later by RateCalculatorService
    private CrossRateCalculationCallback calculationCallback;
    
    // Track which cross rates need recalculation
    private final Set<String> pendingCrossRates = ConcurrentHashMap.newKeySet();
    
    // Map to track dependencies and their actual values
    private final Map<String, Set<String>> missingDependencies = new ConcurrentHashMap<>();
    
    // Add timestamp tracking for calculations to prevent redundant calculations
    private final Map<String, Long> lastCalculationTimes = new ConcurrentHashMap<>();
    private static final long RECALCULATION_THRESHOLD_MS = 1000; // 1 second threshold
    
    public CrossRateCollector(
            RateCacheService rateCacheService,
            RateDependencyManager rateDependencyManager) {
        this.rateCacheService = rateCacheService;
        this.rateDependencyManager = rateDependencyManager;
        log.info("CrossRateCollector initialized");
    }
    
    /**
     * Set the calculation callback - called by RateCalculatorService after initialization
     */
    public void setCalculationCallback(CrossRateCalculationCallback callback) {
        this.calculationCallback = callback;
        log.info("CrossRateCollector: Calculation callback set");
    }
    
    /**
     * Process chain calculations based on a newly calculated rate.
     */
    public void processChainCalculationsForRate(BaseRateDto calculatedRate) {
        if (calculatedRate == null || calculatedRate.getSymbol() == null) {
            log.debug("Null or invalid calculated rate provided for chain processing");
            return;
        }
        
        if (calculationCallback == null) {
            log.warn("Cannot process chain calculations - calculation callback not set");
            return;
        }
        
        String rateSymbol = calculatedRate.getSymbol();
        log.info("Processing chain calculations for {}: bid={}, ask={}", 
                 rateSymbol, calculatedRate.getBid(), calculatedRate.getAsk());
        
        // Verify this is a valid rate
        if (calculatedRate.getBid() == null || calculatedRate.getAsk() == null) {
            log.warn("Cannot use rate {} for chain calculation: missing bid/ask values", rateSymbol);
            return;
        }
        
        // Try lookup with various symbol formats
        List<String> symbolVariants = SymbolUtils.generateSymbolVariants(rateSymbol);
        
        // Find rules that depend on this calculated rate (try all variants)
        List<CalculationRuleDto> dependentRules = new ArrayList<>();
        for (String variant : symbolVariants) {
            List<CalculationRuleDto> rules = rateDependencyManager.getCalculationsToTrigger(variant, false);
            dependentRules.addAll(rules);
        }
        
        if (dependentRules.isEmpty()) {
            log.debug("No dependent rules found for {} (checked variants: {})", 
                     rateSymbol, String.join(", ", symbolVariants));
            return;
        }
        
        log.info("Found {} dependent rules for {}", dependentRules.size(), rateSymbol);
        
        // Process each dependent rule
        for (CalculationRuleDto rule : dependentRules) {
            processRuleIfDependenciesSatisfied(rule);
        }
    }
    
    /**
     * Check if all dependencies for a rule are satisfied and process it if they are
     */
    private void processRuleIfDependenciesSatisfied(CalculationRuleDto rule) {
        if (calculationCallback == null) {
            log.warn("Cannot process rule {} - calculation callback not set", rule.getOutputSymbol());
            pendingCrossRates.add(rule.getOutputSymbol());
            return;
        }

        // Track missing dependencies for better diagnostics
        Set<String> missing = new HashSet<>();
        
        // Check if all calculated dependencies are available
        boolean canCalculate = true;
        Map<String, BaseRateDto> inputRates = new HashMap<>();
        
        if (rule.getDependsOnCalculated() != null) {
            for (String dependency : rule.getDependsOnCalculated()) {
                // Try to find the dependency with or without prefix
                String lookupKey = dependency;
                Optional<BaseRateDto> dependencyRate = rateCacheService.getCalculatedRate(lookupKey);
                
                // Try various symbol formats if the direct lookup fails
                if (dependencyRate.isEmpty()) {
                    for (String variant : SymbolUtils.generateSymbolVariants(dependency)) {
                        dependencyRate = rateCacheService.getCalculatedRate(variant);
                        if (dependencyRate.isPresent()) {
                            lookupKey = variant;
                            log.debug("Found alternative key for dependency {}: {}", dependency, variant);
                            break;
                        }
                    }
                }
                
                if (dependencyRate.isPresent()) {
                    // Get the rate and validate it has proper values
                    BaseRateDto rate = dependencyRate.get();
                    if (rate.getBid() == null || rate.getAsk() == null) {
                        log.warn("Found dependency {} but it has null bid/ask values", dependency);
                        canCalculate = false;
                        missing.add(dependency + " (has null values)");
                        continue;
                    }
                    
                    // Log the rate details for debugging
                    log.debug("Found dependency {} for rule {}: rate={} bid={}, ask={}", 
                        dependency, rule.getOutputSymbol(), rate.getSymbol(), rate.getBid(), rate.getAsk());
                    
                    // Use original dependency name as key for proper script integration  
                    inputRates.put(dependency, rate);
                } else {
                    canCalculate = false;
                    missing.add(dependency);
                    log.debug("Missing dependency {} for rule {}", dependency, rule.getOutputSymbol());
                }
            }
        }
        
        // If we have all dependencies, calculate the chain rule
        if (canCalculate) {
            String outputSymbol = rule.getOutputSymbol();
            long now = System.currentTimeMillis();
            
            // Check if we recently calculated this symbol to avoid redundant calculations
            Long lastCalc = lastCalculationTimes.get(outputSymbol);
            if (lastCalc != null && (now - lastCalc) < RECALCULATION_THRESHOLD_MS) {
                log.debug("Skipping recent calculation for {}, last calculated {}ms ago",
                    outputSymbol, now - lastCalc);
                return;
            }
            
            log.info("All dependencies available for cross rate calculation: {}", outputSymbol);
            boolean success = calculationCallback.calculateRateFromRule(rule, inputRates);
            if (success) {
                pendingCrossRates.remove(rule.getOutputSymbol());
                missingDependencies.remove(rule.getOutputSymbol());
                lastCalculationTimes.put(outputSymbol, now);
                log.info("Successfully calculated cross rate: {}", rule.getOutputSymbol());
            } else {
                log.warn("Failed to calculate cross rate: {}", rule.getOutputSymbol());
            }
        } else {
            // Add to pending cross rates for later calculation
            pendingCrossRates.add(rule.getOutputSymbol());
            missingDependencies.put(rule.getOutputSymbol(), missing);
            log.info("Cross rate {} added to pending list. Missing dependencies: {}", 
                    rule.getOutputSymbol(), String.join(", ", missing));
        }
    }
    
    /**
     * Process all possible chain calculations
     */
    public void processAllPendingChainCalculations() {
        if (pendingCrossRates.isEmpty() || calculationCallback == null) {
            return;
        }
        
        log.info("Processing {} pending cross rate calculations", pendingCrossRates.size());
        
        // Find all unique rules that should be processed
        List<CalculationRuleDto> rulesToProcess = new ArrayList<>();
        
        // First collect rules for pending cross rates
        for (String pendingCrossRate : new HashSet<>(pendingCrossRates)) {
            // Find the rule that produces this cross rate
            List<CalculationRuleDto> rules = rateDependencyManager.getAllRulesSortedByPriority().stream()
                .filter(r -> r.getOutputSymbol().equals(pendingCrossRate) || 
                         SymbolUtils.symbolsEquivalent(r.getOutputSymbol(), pendingCrossRate))
                .toList();
                
            if (!rules.isEmpty()) {
                // Add deduplication logic to prevent adding multiple rules for the same output symbol
                for (CalculationRuleDto rule : rules) {
                    if (rulesToProcess.stream().noneMatch(r -> r.getOutputSymbol().equals(rule.getOutputSymbol()))) {
                        rulesToProcess.add(rule);
                        log.debug("Added rule for pending cross rate {}: {}", pendingCrossRate, rule.getOutputSymbol());
                    } else {
                        log.debug("Skipping duplicate rule for pending cross rate {}: {}", pendingCrossRate, rule.getOutputSymbol());
                    }
                }
            } else {
                log.warn("No rule found for pending cross rate: {}", pendingCrossRate);
            }
        }
        
        log.info("Processing {} unique rules after deduplication", rulesToProcess.size());
        
        // Now process each rule
        for (CalculationRuleDto rule : rulesToProcess) {
            processRuleIfDependenciesSatisfied(rule);
        }
        
        // Log any remaining pending rates with their missing dependencies
        if (!pendingCrossRates.isEmpty()) {
            log.info("{} cross rates still pending after processing", pendingCrossRates.size());
            for (String pendingRate : pendingCrossRates) {
                Set<String> missing = missingDependencies.getOrDefault(pendingRate, Collections.emptySet());
                log.info("Pending rate {}: missing dependencies: {}", pendingRate, String.join(", ", missing));
            }
        }
    }
    
    /**
     * Mark a cross rate as successfully calculated and remove from pending
     */
    public void markCrossRateCalculated(String symbol) {
        if (pendingCrossRates.remove(symbol)) {
            missingDependencies.remove(symbol);
            log.debug("Removed {} from pending cross rates", symbol);
        }
    }
}
