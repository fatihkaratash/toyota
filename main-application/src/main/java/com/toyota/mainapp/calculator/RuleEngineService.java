package com.toyota.mainapp.calculator;

import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;

import java.util.List;
import java.util.Map;

/**
 * Service interface for the rule engine that manages calculation rules
 */
public interface RuleEngineService {

    /**
     * Load calculation rules from configuration
     */
    void loadRules();
    
    /**
     * Get rules that depend on the given input symbol
     */
    List<CalculationRuleDto> getRulesByInputSymbol(String symbol);
    
    /**
     * Get rules that depend on the given base symbol 
     * (base symbol is derived from provider-specific symbol)
     */
    List<CalculationRuleDto> getRulesByInputBaseSymbol(String baseSymbol);
    
    /**
     * Execute a calculation rule with the provided input rates
     */
    BaseRateDto executeRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates);
    
    /**
     * Get all rules
     */
    List<CalculationRuleDto> getAllRules();
    
    /**
     * Add a new rule
     */
    void addRule(CalculationRuleDto rule);
}
