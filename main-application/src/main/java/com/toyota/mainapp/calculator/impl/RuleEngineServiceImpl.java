package com.toyota.mainapp.calculator.impl;

import com.toyota.mainapp.calculator.RuleEngineService;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of the rule engine service
 */
@Service
@Slf4j
public class RuleEngineServiceImpl implements RuleEngineService {

    private final ApplicationContext context;
    private final Map<String, CalculationStrategy> strategies = new ConcurrentHashMap<>();
    private final List<CalculationRuleDto> rules = new ArrayList<>();
    
    public RuleEngineServiceImpl(ApplicationContext context) {
        this.context = context;
    }

    @PostConstruct
    public void init() {
        loadStrategies();
        loadRules();
    }
    
    private void loadStrategies() {
        log.info("Loading calculation strategies...");
        
        Map<String, CalculationStrategy> strategyBeans = context.getBeansOfType(CalculationStrategy.class);
        strategyBeans.forEach((name, strategy) -> {
            String strategyName = strategy.getStrategyName();
            strategies.put(strategyName, strategy);
            log.info("Strategy loaded: {} ({})", strategyName, strategy.getClass().getSimpleName());
        });
        
        log.info("Total {} calculation strategies loaded", strategies.size());
    }

    @Override
    public void loadRules() {
        log.info("Loading calculation rules...");
        
        // Sample rule for testing
        CalculationRuleDto sampleRule = CalculationRuleDto.builder()
                .outputSymbol("USDTRY_AVG")
                .description("USD/TRY average from multiple providers")
                .strategyType("JAVA_CLASS")
                .implementation("averageCalculationStrategy")
                .dependsOnRaw(new String[]{"TCPProvider2_USDTRY", "RESTProvider1_USDTRY"})
                .priority(10)
                .build();
                
        rules.add(sampleRule);
        
        log.info("Total {} calculation rules loaded", rules.size());
    }

    @Override
    public List<CalculationRuleDto> getRulesByInputSymbol(String symbol) {
        return rules.stream()
                .filter(rule -> {
                    String[] dependsOnRaw = rule.getDependsOnRaw();
                    if (dependsOnRaw == null) {
                        return false;
                    }
                    
                    for (String rawSymbol : dependsOnRaw) {
                        if (rawSymbol.equals(symbol)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CalculationRuleDto> getRulesByInputBaseSymbol(String baseSymbol) {
        return rules.stream()
                .filter(rule -> {
                    String[] dependsOnRaw = rule.getDependsOnRaw();
                    if (dependsOnRaw == null) {
                        return false;
                    }
                    
                    for (String rawSymbol : dependsOnRaw) {
                        String rawBaseSymbol = deriveBaseSymbol(rawSymbol);
                        if (rawBaseSymbol.equals(baseSymbol)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }
    
    private String deriveBaseSymbol(String providerSymbol) {
        if (providerSymbol == null) {
            return "";
        }
        
        int underscoreIndex = providerSymbol.indexOf('_');
        return underscoreIndex > 0 ? providerSymbol.substring(underscoreIndex + 1) : providerSymbol;
    }

    @Override
    public BaseRateDto executeRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        String strategyName = rule.getImplementation();
        CalculationStrategy strategy = strategies.get(strategyName);
        
        if (strategy == null) {
            log.error("Strategy not found for rule: {}, strategy: {}", rule.getOutputSymbol(), strategyName);
            return null;
        }
        
        try {
            Optional<BaseRateDto> result = strategy.calculate(rule, inputRates);
            return result.orElse(null);
        } catch (Exception e) {
            log.error("Error executing rule: {}", rule.getOutputSymbol(), e);
            return null;
        }
    }

    @Override
    public List<CalculationRuleDto> getAllRules() {
        return new ArrayList<>(rules);
    }

    @Override
    public void addRule(CalculationRuleDto rule) {
        if (rule != null) {
            rules.add(rule);
            log.info("New rule added: {}", rule.getOutputSymbol());
        }
    }
}
