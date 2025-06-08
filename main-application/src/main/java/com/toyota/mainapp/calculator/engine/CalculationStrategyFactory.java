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
 * ✅ FACTORY PATTERN: Strategy discovery and instantiation
 * Automatically registers all CalculationStrategy implementations via Spring DI
 */
@Component
@Slf4j
public class CalculationStrategyFactory {
    
    private final Map<String, CalculationStrategy> strategiesByName;
    private final Map<String, CalculationStrategy> strategiesByType;
    
    /**
     * ✅ SPRING DI: Auto-discovery of all strategy implementations
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
        
        // Index by strategy type (for type-based filtering)
        this.strategiesByType = strategyList.stream()
                .collect(Collectors.toMap(
                    CalculationStrategy::getStrategyType,
                    Function.identity(),
                    (existing, replacement) -> existing // Keep first one for each type
                ));
                
        log.info("✅ CalculationStrategyFactory initialized with {} strategies:", strategiesByName.size());
        strategiesByName.forEach((name, strategy) -> 
            log.info("  - {} (type: {}, class: {})", 
                    name, strategy.getStrategyType(), strategy.getClass().getSimpleName())
        );
    }
    
    /**
     * ✅ PRIMARY LOOKUP: Get strategy for rule (used by pipeline stages)
     * Simplified single-path lookup with clear precedence
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
    
    /**
     * ✅ TYPE LOOKUP: Get strategy by calculation type (AVG, CROSS)
     */
    public CalculationStrategy getStrategyByType(String type) {
        CalculationStrategy strategy = strategiesByType.get(type);
        if (strategy != null) {
            log.debug("✅ Strategy found by type: {} -> {}", type, strategy.getStrategyName());
        } else {
            log.warn("❌ No strategy found for type: {}", type);
        }
        return strategy;
    }
    
    /**
     * ✅ DISCOVERY: Get strategy by exact name
     */
    public Optional<CalculationStrategy> getStrategyByName(String name) {
        return Optional.ofNullable(strategiesByName.get(name));
    }
    
    /**
     * ✅ VALIDATION: Check if strategy is available for rule
     */
    public boolean isStrategyAvailable(CalculationRuleDto rule) {
        CalculationStrategy strategy = getStrategyForRule(rule);
        return strategy != null;
    }
    
    /**
     * ✅ DISCOVERY: List all available strategies
     */
    public List<CalculationStrategy> getAllStrategies() {
        return List.copyOf(strategiesByName.values());
    }
    
    /**
     * ✅ DISCOVERY: Get available strategy names
     */
    public List<String> getAvailableStrategyNames() {
        return List.copyOf(strategiesByName.keySet());
    }
    
    /**
     * ✅ DISCOVERY: Get available strategy types
     */
    public List<String> getAvailableStrategyTypes() {
        return List.copyOf(strategiesByType.keySet());
    }
    
    /**
     * ✅ VALIDATION: Validate all loaded rules have available strategies
     */
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