package com.toyota.mainapp.calculator.impl;

import com.toyota.mainapp.calculator.RuleEngineService;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import com.toyota.mainapp.util.SymbolUtils;


// Add import for RateDependencyManager
import com.toyota.mainapp.calculator.dependency.RateDependencyManager;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Implementation of the rule engine service
 */
@Service
@Slf4j
public class RuleEngineServiceImpl implements RuleEngineService {

    private final ApplicationContext context;
    private final Map<String, CalculationStrategy> strategies = new ConcurrentHashMap<>();
    // Add this field
    private final RateDependencyManager rateDependencyManager;
    // Consolidated list for active rules, ensuring thread safety for modifications and reads.
    private List<CalculationRuleDto> activeRules = new CopyOnWriteArrayList<>();
    
    public RuleEngineServiceImpl(ApplicationContext context, RateDependencyManager rateDependencyManager) {
        this.context = context;
        this.rateDependencyManager = rateDependencyManager;
    }

    @PostConstruct
    public void init() {
        loadStrategies();
        // loadRules() is now emptied as rules are set externally.
        // If there's any other initialization logic for rules, it can go here,
        // but primary loading is via setCalculationRules.
        log.info("RuleEngineServiceImpl initialized. Strategies loaded. Rules will be set by CalculationConfigLoader.");
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
        // This method is now largely a placeholder as rules are injected via setCalculationRules.
        // Original sample rule loading is removed.
        log.info("loadRules() called. Rules are expected to be set externally via setCalculationRules().");
        // If there's a need to load default or fallback rules programmatically (not from config),
        // that logic could go here, but it should be coordinated with external configuration.
    }

    @Override
    public List<CalculationRuleDto> getRulesByInputSymbol(String symbol) {
        return activeRules.stream()
                .filter(rule -> rule.getDependsOnRaw() != null && rule.getDependsOnRaw().contains(symbol))
                .collect(Collectors.toList());
    }

    @Override
    public List<CalculationRuleDto> getRulesByInputBaseSymbol(String baseSymbol) {
        return activeRules.stream()
                .filter(rule -> {
                    List<String> dependsOnRaw = rule.getDependsOnRaw();
                    if (dependsOnRaw == null) {
                        return false;
                    }
                    for (String rawSymbol : dependsOnRaw) {
                        String derived = deriveBaseSymbol(rawSymbol); // Use the corrected deriveBaseSymbol
                        if (derived.equals(baseSymbol)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }
    
   private String deriveBaseSymbol(String providerSymbol) {
    return SymbolUtils.deriveBaseSymbol(providerSymbol);
}

    @Override
    public BaseRateDto executeRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        CalculationStrategy strategy;
        String strategyLookupKey;

        if ("GROOVY_SCRIPT".equalsIgnoreCase(rule.getStrategyType())) {
            // For Groovy scripts, the strategy is always the GroovyScriptCalculationStrategy bean.
            // The rule.getImplementation() is the script path, used by the strategy itself.
            strategyLookupKey = "groovyScriptCalculationStrategy"; // Bean name of GroovyScriptCalculationStrategy
        } else if ("JAVA_CLASS".equalsIgnoreCase(rule.getStrategyType())) {
            // For Java classes, the rule.getImplementation() is the bean name of the specific strategy.
            strategyLookupKey = rule.getImplementation();
        } else {
            log.error("Unsupported strategy type '{}' for rule: {}", rule.getStrategyType(), rule.getOutputSymbol());
            return null;
        }

        strategy = strategies.get(strategyLookupKey);
        
        if (strategy == null) {
            log.error("Strategy not found for rule: {}, strategyType: {}, lookupKey: {}, availableStrategies: {}",
                    rule.getOutputSymbol(), rule.getStrategyType(), strategyLookupKey, strategies.keySet());
            return null;
        }
        
        try {
            log.debug("Executing rule {} with strategy {} (lookupKey: {})", rule.getOutputSymbol(), strategy.getClass().getSimpleName(), strategyLookupKey);
            Optional<BaseRateDto> result = strategy.calculate(rule, inputRates);
            return result.orElse(null);
        } catch (Exception e) {
            log.error("Error executing rule: {}", rule.getOutputSymbol(), e);
            return null;
        }
    }

    @Override
    public List<CalculationRuleDto> getAllRules() {
        return Collections.unmodifiableList(new ArrayList<>(activeRules)); // Return a copy
    }

    @Override
    public void addRule(CalculationRuleDto rule) {
        if (rule != null) {
            // Ensure not to add duplicates if outputSymbol is a unique key
            if (activeRules.stream().noneMatch(r -> r.getOutputSymbol().equals(rule.getOutputSymbol()))) {
                activeRules.add(rule);
                log.info("New rule added: {}", rule.getOutputSymbol());
            } else {
                log.warn("Rule with outputSymbol {} already exists. Not adding.", rule.getOutputSymbol());
            }
        }
    }

    @Override
    public void setCalculationRules(List<CalculationRuleDto> rules) {
        this.activeRules.clear();
        if (rules != null) {
            // Sort rules by priority (lower value = higher priority) before adding to activeRules
            List<CalculationRuleDto> sortedRules = new ArrayList<>(rules);
            sortedRules.sort(Comparator.comparing(CalculationRuleDto::getPriority));
            this.activeRules.addAll(sortedRules);
            
            log.info("{} adet hesaplama kuralı RuleEngineService'e set edildi.", this.activeRules.size());
            if (rateDependencyManager != null) {
                rateDependencyManager.buildDependencyGraph(this.activeRules);
                log.info("Hesaplama kuralları için bağımlılık grafiği oluşturuldu.");
            } else {
                log.warn("RateDependencyManager is null, cannot build dependency graph.");
            }
        } else {
            log.warn("RuleEngineService'e null kural listesi set edilmeye çalışıldı. Aktif kurallar temizlendi.");
        }
    }

    @Override
    public List<CalculationRuleDto> getCalculationRules() { 
        return Collections.unmodifiableList(this.activeRules); 
    }

    @Override
    public List<CalculationRuleDto> getRulesDependingOnCalculatedRate(String outputSymbol) {
        // Returns rules that depend on the given outputSymbol as an input
        return activeRules.stream()
                .filter(rule -> rule.getDependsOnRaw() != null && rule.getDependsOnRaw().contains(outputSymbol))
                .collect(Collectors.toList());
    }
    
}
