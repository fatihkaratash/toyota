package com.toyota.mainapp.calculator.engine;

import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;

import java.util.Map;
import java.util.Optional;

/**
 * Config-driven calculation execution with standardized input/output
 */
public interface CalculationStrategy {
    
    /**
     * EXECUTE CALCULATION: Core strategy method
     * @param rule Configuration rule defining the calculation
     * @param inputRates Map of symbol -> BaseRateDto (raw or calculated rates)
     * @return Optional calculated rate (empty if calculation fails)
     */
    Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates);
    
    /**
     * Must match @Component bean name
     * @return Strategy name for factory discovery
     */
    String getStrategyName();
    
    /**
     * : Category for rule filtering  
     * @return Strategy type (AVG, CROSS, etc.) - must match CalculationRuleType enum codes
     */
    default String getStrategyType() {
        return "CUSTOM"; // Default to CUSTOM instead of GENERIC
    }
    
    /**
      Check if strategy can handle the rule
     * @param rule Configuration rule to validate
     * @return true if strategy can process this rule
     */
    default boolean canHandle(CalculationRuleDto rule) {
        if (rule == null) return false;

        String ruleType = rule.getType();
        if (ruleType != null && ruleType.equals(getStrategyType())) {
            return true;
        }

        String ruleStrategyType = rule.getStrategyType();
        return ruleStrategyType != null && ruleStrategyType.equals(getStrategyName());
    }
}

