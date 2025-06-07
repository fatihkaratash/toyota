package com.toyota.mainapp.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}