package com.toyota.mainapp.calculator.engine;

import com.toyota.mainapp.dto.config.CalculationRuleDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Toyota Financial Data Platform - Calculation Strategy Factory
 * 
 * Auto-discovery factory for calculation strategy implementations using Spring DI.
 * Provides strategy lookup by name and type with validation capabilities for
 * the dynamic rate calculation engine within the financial data platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Component
@Slf4j
public class CalculationStrategyFactory {
    
    private final Map<String, CalculationStrategy> strategiesByName;
    private final Map<String, CalculationStrategy> strategiesByType;
    
    /**
     *  SPRING DI: Auto-discovery of all strategy implementations
     */
    public CalculationStrategyFactory(List<CalculationStrategy> strategyList) {
        // Index by strategy name (for exact matching)
        this.strategiesByName = strategyList.stream()
                .collect(Collectors.toMap(
                    CalculationStrategy::getStrategyName,
                    Function.identity(),
                    (existing, replacement) -> {
                        log.warn("Duplicate strategy name: {}. Using: {}", 
                                existing.getStrategyName(), replacement.getClass().getSimpleName());
                        return replacement;
                    }
                ));

        this.strategiesByType = strategyList.stream()
                .collect(Collectors.toMap(
                    CalculationStrategy::getStrategyType,
                    Function.identity(),
                    (existing, replacement) -> existing 
                ));
                
        log.info("✅ CalculationStrategyFactory initialized with {} strategies:", strategiesByName.size());
        strategiesByName.forEach((name, strategy) -> 
            log.info("  - {} (type: {}, class: {})", 
                    name, strategy.getStrategyType(), strategy.getClass().getSimpleName())
        );
    }
    
    /**
     *  PRIMARY LOOKUP: Get strategy for rule (used by pipeline stages)
     */
    public CalculationStrategy getStrategyForRule(CalculationRuleDto rule) {
        if (rule == null) {
            log.warn("Cannot get strategy for null rule");
            return null;
        }
        
        // 1. PRIMARY: Try exact strategy name match first
        if (rule.getStrategyType() != null) {
            CalculationStrategy strategy = strategiesByName.get(rule.getStrategyType());
            if (strategy != null && strategy.canHandle(rule)) {
                log.debug("✅ Strategy found by name: {} for rule: {}", 
                        strategy.getStrategyName(), rule.getOutputSymbol());
                return strategy;
            }
        }
        
        // 2. FALLBACK: Try by rule type (AVG, CROSS)
        if (rule.getType() != null) {
            CalculationStrategy strategy = strategiesByType.get(rule.getType());
            if (strategy != null && strategy.canHandle(rule)) {
                log.debug("✅ Strategy found by type: {} -> {} for rule: {}", 
                        rule.getType(), strategy.getStrategyName(), rule.getOutputSymbol());
                return strategy;
            }
        }
        
        log.warn("❌ No suitable strategy found for rule: {} (strategyType: {}, type: {})", 
                rule.getOutputSymbol(), rule.getStrategyType(), rule.getType());
        return null;
    }

    public CalculationStrategy getStrategyByType(String type) {
        CalculationStrategy strategy = strategiesByType.get(type);
        if (strategy != null) {
            log.debug("✅ Strategy found by type: {} -> {}", type, strategy.getStrategyName());
        } else {
            log.warn("❌ No strategy found for type: {}", type);
        }
        return strategy;
    }
 
    public Optional<CalculationStrategy> getStrategyByName(String name) {
        return Optional.ofNullable(strategiesByName.get(name));
    }

    public boolean isStrategyAvailable(CalculationRuleDto rule) {
        CalculationStrategy strategy = getStrategyForRule(rule);
        return strategy != null;
    }

    public List<CalculationStrategy> getAllStrategies() {
        return List.copyOf(strategiesByName.values());
    }

    public List<String> getAvailableStrategyNames() {
        return List.copyOf(strategiesByName.keySet());
    }

    public List<String> getAvailableStrategyTypes() {
        return List.copyOf(strategiesByType.keySet());
    }
    
    public void validateRules(List<CalculationRuleDto> rules) {
        for (CalculationRuleDto rule : rules) {
            if (!isStrategyAvailable(rule)) {
                throw new IllegalStateException(
                    "No suitable strategy found for rule: " + rule.getOutputSymbol() + 
                    " (strategyType: " + rule.getStrategyType() + ", type: " + rule.getType() + ")"
                );
            }
        }
        log.info("✅ All {} calculation rules validated successfully", rules.size());
    }
}