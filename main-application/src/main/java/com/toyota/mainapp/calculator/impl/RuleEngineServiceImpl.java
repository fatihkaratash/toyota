package com.toyota.mainapp.calculator.impl;

import com.toyota.mainapp.calculator.RuleEngineService;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rule Engine Service Implementation
 * ✅ ENHANCED: Strategy execution added
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuleEngineServiceImpl implements RuleEngineService {
    
    private final CalculationStrategy calculationStrategy;
    
    private final Map<String, CalculationRuleDto> rulesByOutputSymbol = new ConcurrentHashMap<>();
    private final Map<String, List<CalculationRuleDto>> rulesByInputSymbol = new ConcurrentHashMap<>();
    private volatile List<CalculationRuleDto> allRules = Collections.emptyList();
    
    @Override
    public void setCalculationRules(List<CalculationRuleDto> rules) {
        if (rules == null) {
            rules = Collections.emptyList();
        }
        
        log.info("setCalculationRules called with {} rules", rules.size());
        for (CalculationRuleDto rule : rules) {
            log.debug("Loading rule: {} -> {} (inputs: {})", 
                    rule.getOutputSymbol(), rule.getStrategyType(), 
                    String.join(",", rule.getInputSymbols()));
        }
        
        synchronized (this) {
            // Clear existing data
            rulesByOutputSymbol.clear();
            rulesByInputSymbol.clear();
            
            // Build lookup maps
            for (CalculationRuleDto rule : rules) {
                // By output symbol
                rulesByOutputSymbol.put(rule.getOutputSymbol(), rule);
                
                // By input symbols
                for (String inputSymbol : rule.getInputSymbols()) {
                    rulesByInputSymbol.computeIfAbsent(inputSymbol, k -> new ArrayList<>()).add(rule);
                }
            }
            
            allRules = Collections.unmodifiableList(new ArrayList<>(rules));
        }
        
        log.info("Rules engine updated: {} rules loaded, {} by output symbol, {} by input symbol", 
                rules.size(), rulesByOutputSymbol.size(), rulesByInputSymbol.size());
    }
    
    @Override
    public List<CalculationRuleDto> getCalculationRules() {
        log.debug("getCalculationRules() returning {} rules", allRules.size());
        return allRules;
    }
    
    @Override
    public CalculationRuleDto getRuleByOutputSymbol(String outputSymbol) {
        return rulesByOutputSymbol.get(outputSymbol);
    }
    
    @Override
    public List<CalculationRuleDto> getRulesByInputSymbol(String inputSymbol) {
        return rulesByInputSymbol.getOrDefault(inputSymbol, Collections.emptyList());
    }
    
    @Override
    public boolean hasRules() {
        boolean hasRules = !allRules.isEmpty();
        log.debug("hasRules() = {}, total rules: {}", hasRules, allRules.size());
        return hasRules;
    }
    
    /**
     * ✅ KEY METHOD: Execute rule using config-driven strategy selection
     * Called by stages for both AVG and CROSS calculations
     */
    @Override
    public Optional<BaseRateDto> executeRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        if (rule == null || inputRates == null) {
            log.warn("Invalid rule or input rates for execution");
            return Optional.empty();
        }

        try {
            // ✅ CONFIG-DRIVEN: Use strategy specified in rule configuration
            log.debug("Executing rule: {} with strategy: {} (type: {})", 
                    rule.getOutputSymbol(), rule.getImplementation(), rule.getStrategyType());
            
            // Delegate to strategy - the strategy implementation will handle
            // config-driven logic based on rule.getImplementation()
            return calculationStrategy.calculate(rule, inputRates);
            
        } catch (Exception e) {
            log.error("Rule execution failed for {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
            return Optional.empty();
        }
    }
}