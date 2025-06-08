package com.toyota.mainapp.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 Single source of truth for calculation rules
 * Used by: ApplicationProperties, CalculationStrategyFactory, Pipeline Stages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationRuleDto {
    
    private String outputSymbol;
    private String description;
    private String type;               //  Rule type (AVG, CROSS) -
    private String strategyType;       
    private String implementation;     
    
    private List<String> inputSymbols; 
    private Map<String, Object> inputParameters; // 
    

    public CalculationRuleType getTypeEnum() {
        return CalculationRuleType.fromString(this.type);
    }

    public void setTypeEnum(CalculationRuleType typeEnum) {
        this.type = typeEnum != null ? typeEnum.getCode() : null;
    }

    public boolean isValidType() {
        return CalculationRuleType.isValidType(this.type);
    }

    public boolean isGroovyStrategy() {
        return implementation != null && 
               (implementation.endsWith(".groovy") || implementation.contains("groovy"));
    }

    public boolean isJavaStrategy() {
        return strategyType != null && 
               (strategyType.contains("Strategy") || strategyType.contains("CalculationStrategy"));
    }

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

    public Double getWeightForSymbol(String symbol) {
        if (inputParameters == null || symbol == null) {
            return 1.0; 
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
        
        return 1.0;
    }
}