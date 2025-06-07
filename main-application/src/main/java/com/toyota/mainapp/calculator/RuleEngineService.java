package com.toyota.mainapp.calculator;

import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rule Engine Service Interface
 * Calculation rules yönetimi ve execution için servis arayüzü
 */
public interface RuleEngineService {
    
    /**
     * Calculation rules'ları set et
     */
    void setCalculationRules(List<CalculationRuleDto> rules);
    
    /**
     * Tüm calculation rules'ları al
     */
    List<CalculationRuleDto> getCalculationRules();
    
    /**
     * Output symbol'a göre rule al
     */
    CalculationRuleDto getRuleByOutputSymbol(String outputSymbol);
    
    /**
     * Input symbol'a göre rules al
     */
    List<CalculationRuleDto> getRulesByInputSymbol(String inputSymbol);
    
    /**
     * Rule'lar yüklenmiş mi kontrol et
     */
    boolean hasRules();
    
    /**
     * ✅ NEW: Rule'ı execute et - Strategy pattern delegation
     */
    Optional<BaseRateDto> executeRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates);
}