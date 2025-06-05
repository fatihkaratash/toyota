package com.toyota.mainapp.calculator;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import java.util.List;
import java.util.Map;

/**
 * Service interface for the rule engine that manages calculation rules
 */
public interface RuleEngineService {
    

    void setCalculationRules(List<CalculationRuleDto> rules);
    List<CalculationRuleDto> getCalculationRules();
    void loadRules();
    List<CalculationRuleDto> getRulesByInputSymbol(String symbol);
    List<CalculationRuleDto> getRulesByInputBaseSymbol(String baseSymbol);
    BaseRateDto executeRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates);
    List<CalculationRuleDto> getAllRules();
    void addRule(CalculationRuleDto rule);
    List<CalculationRuleDto> getRulesDependingOnCalculatedRate(String calculatedRateSymbol);
}
