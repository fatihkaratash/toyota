package com.toyota.mainapp.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ✅ CONSOLIDATED: Single source of truth for calculation rules
 * Used by: ApplicationProperties, CalculationStrategyFactory, Pipeline Stages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationRuleDto {
    
    private String outputSymbol;        // ✅ USED: Target symbol (USDTRY_AVG, EURTRY_CROSS)
    private String description;         // ✅ USED: Human readable description
    
    // ✅ ENUM INTEGRATION: Type safety with enum support
    private String type;               // ✅ USED: Rule type (AVG, CROSS) - for stage filtering
    private String strategyType;       // ✅ USED: Strategy bean name for factory lookup
    
    // ✅ GROOVY SUPPORT: Implementation path for script strategies
    private String implementation;     // ✅ USED: Script path for GroovyScriptCalculationStrategy
    
    private List<String> inputSymbols; // ✅ USED: Required input symbols
    private Map<String, Object> inputParameters; // ✅ USED: Strategy-specific parameters (Object for flexibility)
    
    /**
     * ✅ ENUM INTEGRATION: Get rule type as enum
     */
    public CalculationRuleType getTypeEnum() {
        return CalculationRuleType.fromString(this.type);
    }
    
    /**
     * ✅ ENUM INTEGRATION: Set rule type from enum
     */
    public void setTypeEnum(CalculationRuleType typeEnum) {
        this.type = typeEnum != null ? typeEnum.getCode() : null;
    }
    
    /**
     * ✅ VALIDATION: Check if rule type is valid
     */
    public boolean isValidType() {
        return CalculationRuleType.isValidType(this.type);
    }
    
    /**
     * ✅ GROOVY DETECTION: Check if this uses Groovy script strategy
     */
    public boolean isGroovyStrategy() {
        return implementation != null && 
               (implementation.endsWith(".groovy") || implementation.contains("groovy"));
    }
    
    /**
     * ✅ JAVA DETECTION: Check if this uses Java strategy
     */
    public boolean isJavaStrategy() {
        return strategyType != null && 
               (strategyType.contains("Strategy") || strategyType.contains("CalculationStrategy"));
    }
    
    /**
     * ✅ BACKWARD COMPATIBILITY: Keep existing getters
     */
    public String getType() {
        return type;
    }
    
    public String getStrategyType() {
        return strategyType;
    }

    /**
     * ✅ NEW: Get raw data sources for immediate pipeline
     * ✅ ACTIVELY USED: Raw rate collection in AverageCalculationStage
     * Usage: getRawSources() for input collection
     */
    public List<String> getRawSources() {
        if (inputSymbols == null) {
            return new ArrayList<>();
        }
        
        // For AVG rules: input symbols are raw rate sources
        if (CalculationRuleType.AVG.equals(getTypeEnum())) {
            return new ArrayList<>(inputSymbols);
        }
        
        // For CROSS rules: may need to extract from parameters
        if (inputParameters != null && inputParameters.containsKey("rawSources")) {
            Object rawSourcesObj = inputParameters.get("rawSources");
            if (rawSourcesObj instanceof List) {
                return (List<String>) rawSourcesObj;
            }
        }
        
        return new ArrayList<>();
    }

    /**
     * ✅ NEW: Get calculated rate dependencies for cross rates
     * ✅ ACTIVELY USED: Dependency collection in CrossRateCalculationStage
     * Usage: getRequiredCalculatedRates() for dependency resolution
     */
    public List<String> getRequiredCalculatedRates() {
        if (inputSymbols == null) {
            return new ArrayList<>();
        }
        
        // For CROSS rules: input symbols are typically calculated rates
        if (CalculationRuleType.CROSS.equals(getTypeEnum())) {
            return new ArrayList<>(inputSymbols);
        }
        
        // Check parameters for calculated dependencies
        if (inputParameters != null && inputParameters.containsKey("calculatedDependencies")) {
            Object calcDepsObj = inputParameters.get("calculatedDependencies");
            if (calcDepsObj instanceof List) {
                return (List<String>) calcDepsObj;
            }
        }
        
        return new ArrayList<>();
    }

    /**
     * ✅ NEW: Check if rule requires specific symbol as input
     * ✅ ACTIVELY USED: Rule filtering in immediate pipeline stages
     * Usage: requiresSymbol() for affected rules detection
     */
    public boolean requiresSymbol(String symbol) {
        if (symbol == null || inputSymbols == null) {
            return false;
        }
        
        String normalizedSymbol = symbol.trim().toUpperCase();
        
        // Direct match
        if (inputSymbols.contains(normalizedSymbol)) {
            return true;
        }
        
        // Check with various formats
        for (String inputSymbol : inputSymbols) {
            if (inputSymbol.equals(normalizedSymbol) ||
                inputSymbol.equals(normalizedSymbol + "_AVG") ||
                inputSymbol.replace("_AVG", "").equals(normalizedSymbol)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * ✅ NEW: Get weight for input symbol (for weighted averages)
     * ✅ ACTIVELY USED: Weighted calculation in AverageCalculationStrategy
     * Usage: getWeightForSymbol() in calculation logic
     */
    public Double getWeightForSymbol(String symbol) {
        if (inputParameters == null || symbol == null) {
            return 1.0; // Default equal weight
        }
        
        // Check for weights map in parameters
        Object weightsObj = inputParameters.get("weights");
        if (weightsObj instanceof Map) {
            Map<String, Object> weights = (Map<String, Object>) weightsObj;
            Object weight = weights.get(symbol);
            if (weight instanceof Number) {
                return ((Number) weight).doubleValue();
            }
        }
        
        return 1.0; // Default equal weight
    }
}