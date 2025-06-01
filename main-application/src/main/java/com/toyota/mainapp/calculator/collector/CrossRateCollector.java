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
import jakarta.annotation.PostConstruct; // Changed from javax to jakarta

@Component
@Slf4j
public class CrossRateCollector {

    private final RateCacheService rateCacheService;
    private final RateDependencyManager rateDependencyManager;
    
    // Callback for calculation - will be set later by RateCalculatorService
    private CrossRateCalculationCallback calculationCallback;
    
    // Track which cross rates need recalculation
    private final Set<String> pendingCrossRates = ConcurrentHashMap.newKeySet();
    
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
     * This is more efficient than checking all cached rates each time.
     */
    public void processChainCalculationsForRate(BaseRateDto calculatedRate) {
        if (calculatedRate == null || calculatedRate.getSymbol() == null) {
            log.debug("Null or invalid calculated rate provided for chain processing");
            return;
        }
        
        if (calculationCallback == null) {
            log.warn("Cannot process chain calculations - calculation callback not set");
            pendingCrossRates.add(calculatedRate.getSymbol());
            return;
        }
        
        String rateSymbol = calculatedRate.getSymbol();
        log.info("Processing chain calculations for {}", rateSymbol);
        
        // Find rules that depend on this calculated rate
        List<CalculationRuleDto> dependentRules = 
                rateDependencyManager.getCalculationsToTrigger(rateSymbol, false);
        
        if (dependentRules.isEmpty()) {
            log.debug("No dependent rules found for {}", rateSymbol);
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
        
        // Check if all calculated dependencies are available
        boolean canCalculate = true;
        Map<String, BaseRateDto> inputRates = new HashMap<>();
        
        for (String dependency : rule.getDependsOnCalculated()) {
            Optional<BaseRateDto> dependencyRate = rateCacheService.getCalculatedRate(dependency);
            if (dependencyRate.isPresent()) {
                inputRates.put(dependency, dependencyRate.get());
            } else {
                canCalculate = false;
                log.debug("Missing dependency {} for rule {}", dependency, rule.getOutputSymbol());
                break;
            }
        }
        
        // If we have all dependencies, calculate the chain rule
        if (canCalculate) {
            log.info("All dependencies available for cross rate calculation: {}", rule.getOutputSymbol());
            boolean success = calculationCallback.calculateRateFromRule(rule, inputRates);
            if (success) {
                pendingCrossRates.remove(rule.getOutputSymbol());
            }
        } else {
            // Add to pending cross rates for later calculation
            pendingCrossRates.add(rule.getOutputSymbol());
            log.info("Cross rate {} added to pending list (missing dependencies)", rule.getOutputSymbol());
        }
    }
    
    /**
     * Process all possible chain calculations - this is more thorough
     * but less efficient than processing individual rates
     */
    public void processAllPendingChainCalculations() {
        if (pendingCrossRates.isEmpty() || calculationCallback == null) {
            return;
        }
        
        log.info("Processing {} pending cross rate calculations", pendingCrossRates.size());
        
        // Get all calculated rates from the cache
        Map<String, BaseRateDto> calculatedRates = rateCacheService.getAllCalculatedRates();
        
        if (calculatedRates.isEmpty()) {
            log.debug("No calculated rates available for cross rate calculations");
            return;
        }
        
        // Find all unique rules that should be processed
        Set<CalculationRuleDto> rulesToProcess = new HashSet<>();
        
        // First collect rules for pending cross rates
        for (String pendingCrossRate : new HashSet<>(pendingCrossRates)) {
            // Find the rule that produces this cross rate
            Optional<CalculationRuleDto> ruleOpt = rateDependencyManager.getAllRulesSortedByPriority().stream()
                .filter(r -> r.getOutputSymbol().equals(pendingCrossRate))
                .findFirst();
                
            if (ruleOpt.isPresent()) {
                rulesToProcess.add(ruleOpt.get());
            }
        }
        
        // Now process each rule
        for (CalculationRuleDto rule : rulesToProcess) {
            processRuleIfDependenciesSatisfied(rule);
        }
    }
    
    /**
     * Mark a cross rate as successfully calculated and remove from pending
     */
    public void markCrossRateCalculated(String symbol) {
        if (pendingCrossRates.remove(symbol)) {
            log.debug("Removed {} from pending cross rates", symbol);
        }
    }
}
