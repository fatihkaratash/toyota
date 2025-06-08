package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.util.RateCalculationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

/**
 * ✅ COMPLETE: Average calculation strategy implementation
 */
@Component("averageCalculationStrategy") // ✅ FIXED: Explicit bean name for consistency
@Slf4j
public class AverageCalculationStrategy implements CalculationStrategy {

    @Override
    public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        try {
            log.debug("Starting average calculation for: {} with {} inputs", 
                    rule.getOutputSymbol(), inputRates.size());
            
            if (inputRates == null || inputRates.isEmpty()) {
                log.warn("No input rates provided for average calculation: {}", rule.getOutputSymbol());
                return Optional.empty();
            }
            
            // Filter rates to only include relevant symbol rates
            Map<String, BaseRateDto> relevantRates = filterRatesForRule(rule, inputRates);
            
            if (relevantRates.isEmpty()) {
                log.warn("No relevant rates found for average calculation: {}", rule.getOutputSymbol());
                return Optional.empty();
            }
            
            // Calculate average using utility
            BaseRateDto averageRate = RateCalculationUtils.calculateAverage(rule, relevantRates);
            
            if (averageRate != null) {
                // ✅ ENSURE: Output symbol matches rule configuration exactly
                averageRate.setSymbol(rule.getOutputSymbol()); // Force correct symbol
                
                // ✅ VALIDATE: Ensure symbol format consistency
                String normalizedSymbol = com.toyota.mainapp.util.SymbolUtils.normalizeSymbol(averageRate.getSymbol());
                if (!rule.getOutputSymbol().equals(normalizedSymbol) && 
                    !rule.getOutputSymbol().equals(averageRate.getSymbol())) {
                    log.warn("Symbol format inconsistency detected: rule={}, calculated={}, normalized={}", 
                            rule.getOutputSymbol(), averageRate.getSymbol(), normalizedSymbol);
                }
                
                log.info("✅ Average calculation completed for: {} from {} rates", 
                        rule.getOutputSymbol(), relevantRates.size());
                return Optional.of(averageRate);
            } else {
                log.warn("Average calculation returned null for: {}", rule.getOutputSymbol());
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("Error in average calculation for {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * ✅ Filter input rates to only include rates relevant to this rule
     */
    private Map<String, BaseRateDto> filterRatesForRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        Map<String, BaseRateDto> filtered = new HashMap<>();
        
        if (rule.getRawSources() == null || rule.getRawSources().isEmpty()) {
            return filtered;
        }
        
        // Include rates that match any of the rule's raw sources
        for (Map.Entry<String, BaseRateDto> entry : inputRates.entrySet()) {
            BaseRateDto rate = entry.getValue();
            if (rate != null && rate.getSymbol() != null) {
                // ✅ NORMALIZE: Use consistent symbol comparison
                String rateSymbol = com.toyota.mainapp.util.SymbolUtils.normalizeSymbol(rate.getSymbol());
                boolean isRelevant = rule.getRawSources().stream()
                        .anyMatch(source -> {
                            String normalizedSource = com.toyota.mainapp.util.SymbolUtils.normalizeSymbol(source);
                            return normalizedSource.equals(rateSymbol) || 
                                   rateSymbol.contains(normalizedSource);
                        });
                
                if (isRelevant) {
                    filtered.put(entry.getKey(), rate);
                }
            }
        }
        
        log.debug("Filtered {} relevant rates from {} total inputs for rule: {}", 
                filtered.size(), inputRates.size(), rule.getOutputSymbol());
        
        return filtered;
    }

    @Override
    public String getStrategyName() {
        return "averageCalculationStrategy"; // ✅ Match @Component bean name for factory registration
    }

    @Override
    public String getStrategyType() {
        return "AVG"; // ✅ This must match CalculationRuleType.AVG.getCode()
    }
    
    @Override
    public boolean canHandle(CalculationRuleDto rule) {
        if (rule == null) return false;
        
        // Handle both AVG type rules and strategy name variations
        return "AVG".equals(rule.getType()) || 
               "AVERAGE".equals(rule.getStrategyType()) ||
               "averageCalculationStrategy".equals(rule.getStrategyType());
    }
}
