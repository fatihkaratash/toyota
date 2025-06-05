package com.toyota.mainapp.calculator.collector;

import com.toyota.mainapp.dto.config.CalculationRuleDto;
import java.util.Map;
import com.toyota.mainapp.dto.model.BaseRateDto;

/**
 * Interface for callbacks to calculate cross rates
 * This breaks the circular dependency between CrossRateCollector and RateCalculatorService
 */
public interface CrossRateCalculationCallback {
    
    boolean calculateRateFromRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates);
}
