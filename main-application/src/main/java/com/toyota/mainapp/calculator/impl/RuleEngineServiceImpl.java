package com.toyota.mainapp.calculator.impl;

import com.toyota.mainapp.calculator.RuleEngineService;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import com.toyota.mainapp.dto.RawRateDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hesaplama kurallarını yöneten ve hesaplamaları yapan servis implementasyonu
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
        log.info("Hesaplama stratejileri yükleniyor...");
        
        Map<String, CalculationStrategy> strategyBeans = context.getBeansOfType(CalculationStrategy.class);
        strategyBeans.forEach((name, strategy) -> {
            String strategyName = strategy.getStrategyName();
            strategies.put(strategyName, strategy);
            log.info("Strateji yüklendi: {} ({})", strategyName, strategy.getClass().getSimpleName());
        });
        
        log.info("Toplam {} hesaplama stratejisi yüklendi", strategies.size());
    }

    @Override
    public void loadRules() {
        // TODO: Rules should be loaded from configuration file
        log.info("Hesaplama kuralları yükleniyor...");
        
        // Updated sample rule with correct provider symbols
        CalculationRuleDto sampleRule = CalculationRuleDto.builder()
                .outputSymbol("USD/TRY_AVG")
                .description("USD/TRY ortalaması")
                .strategyType("JAVA_CLASS")
                .implementation("averageUsdTryStrategy")
                .dependsOnRaw(new String[]{"PF1_USDTRY", "PF2_USDTRY"})
                .priority(10)
                .build();
                
        rules.add(sampleRule);
        
        log.info("Toplam {} hesaplama kuralı yüklendi", rules.size());
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
    public CalculatedRateDto executeRule(CalculationRuleDto rule, Map<String, RawRateDto> inputRates) {
        String strategyName = rule.getImplementation();
        CalculationStrategy strategy = strategies.get(strategyName);
        
        if (strategy == null) {
            log.error("Kural için strateji bulunamadı: {}, strateji: {}", rule.getOutputSymbol(), strategyName);
            return null;
        }
        
        try {
            Optional<CalculatedRateDto> result = strategy.calculate(rule, inputRates);
            return result.orElse(null);
        } catch (Exception e) {
            log.error("Kural çalıştırılırken hata oluştu: {}", rule.getOutputSymbol(), e);
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
            log.info("Yeni kural eklendi: {}", rule.getOutputSymbol());
        }
    }
}
