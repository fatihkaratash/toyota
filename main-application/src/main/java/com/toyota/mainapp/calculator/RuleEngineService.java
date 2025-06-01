package com.toyota.mainapp.calculator;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for the rule engine that manages calculation rules
 */
public interface RuleEngineService {
    
    
    // YENİ METOT: Hesaplama kurallarını ayarlamak için
    void setCalculationRules(List<CalculationRuleDto> rules);

    // YENİ METOT: Yüklenmiş kuralları almak için (RateCalculatorService tarafından kullanılabilir)
    List<CalculationRuleDto> getCalculationRules();

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
    
    /**
     * Get rules that depend on a specific calculated rate
     */
    List<CalculationRuleDto> getRulesDependingOnCalculatedRate(String calculatedRateSymbol);
}
