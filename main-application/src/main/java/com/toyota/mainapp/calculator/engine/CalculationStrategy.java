package com.toyota.mainapp.calculator.engine;

import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;

import java.util.Map;
import java.util.Optional;

/**
 * ✅ STRATEGY PATTERN: Core interface for all calculation strategies
 * Config-driven calculation execution with standardized input/output
 */
public interface CalculationStrategy {
    
    /**
     * ✅ EXECUTE CALCULATION: Core strategy method
     * @param rule Configuration rule defining the calculation
     * @param inputRates Map of symbol -> BaseRateDto (raw or calculated rates)
     * @return Optional calculated rate (empty if calculation fails)
     */
    Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates);
    
    /**
     * ✅ STRATEGY IDENTIFICATION: Must match @Component bean name
     * @return Strategy name for factory discovery
     */
    String getStrategyName();
    
    /**
     * ✅ STRATEGY TYPE: Category for rule filtering  
     * @return Strategy type (AVG, CROSS, etc.)
     */
    default String getStrategyType() {
        return "GENERIC";
    }
    
    /**
     * ✅ VALIDATION: Check if strategy can handle the rule
     * @param rule Configuration rule to validate
     * @return true if strategy can process this rule
     */
    default boolean canHandle(CalculationRuleDto rule) {
        return rule != null && rule.getStrategyType() != null;
    }
}

