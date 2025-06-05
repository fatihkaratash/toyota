package com.toyota.mainapp.calculator.dependency;

import com.toyota.mainapp.dto.config.CalculationRuleDto; 
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.Arrays;

@Component
@Slf4j
public class RateDependencyManager {

    private final Map<String, List<CalculationRuleDto>> directRawDependencies = new ConcurrentHashMap<>();
    private final Map<String, List<CalculationRuleDto>> directCalculatedDependencies = new ConcurrentHashMap<>();
    private final List<CalculationRuleDto> allRulesSortedByPriority = new CopyOnWriteArrayList<>();

    public void buildDependencyGraph(List<CalculationRuleDto> rules) {
        this.allRulesSortedByPriority.clear();
        this.directRawDependencies.clear();
        this.directCalculatedDependencies.clear();

        if (rules == null || rules.isEmpty()) {
            log.warn("No calculation rules provided to build dependency graph.");
            return;
        }

        List<CalculationRuleDto> sortedRules = new ArrayList<>(rules);
        sortedRules.sort(Comparator.comparing(CalculationRuleDto::getPriority));
        
        log.debug("Building dependency graph with rules in priority order:");
        for (CalculationRuleDto rule : sortedRules) {
            log.debug("  Rule: {} (priority: {})", rule.getOutputSymbol(), rule.getPriority());
        }
        
        this.allRulesSortedByPriority.addAll(sortedRules);

        for (CalculationRuleDto rule : sortedRules) {
            if (rule.getDependsOnRaw() != null) {
                for (String rawSymbolKey : rule.getDependsOnRaw()) {
        
                    List<CalculationRuleDto> existingRules = directRawDependencies.computeIfAbsent(rawSymbolKey, k -> new ArrayList<>());
                    if (!containsRuleWithSameOutputSymbol(existingRules, rule)) {
                        existingRules.add(rule);
                    }
                }
            }
            if (rule.getDependsOnCalculated() != null) {
                for (String calculatedSymbolKey : rule.getDependsOnCalculated()) {
                 
                    List<CalculationRuleDto> existingRules = directCalculatedDependencies.computeIfAbsent(calculatedSymbolKey, k -> new ArrayList<>());
                    if (!containsRuleWithSameOutputSymbol(existingRules, rule)) {
                        existingRules.add(rule);
                    }
                }
            }
        }
        log.info("Built dependency graph. Raw dependencies: {}, Calculated dependencies: {}. Total rules: {}",
                directRawDependencies.size(), directCalculatedDependencies.size(), allRulesSortedByPriority.size());
        
        logDependencyGraph();
    }

    private boolean containsRuleWithSameOutputSymbol(List<CalculationRuleDto> rules, CalculationRuleDto newRule) {
        return rules.stream().anyMatch(r -> 
            r.getOutputSymbol().equals(newRule.getOutputSymbol()) || 
            com.toyota.mainapp.util.SymbolUtils.symbolsEquivalent(r.getOutputSymbol(), newRule.getOutputSymbol())
        );
    }
 
    private void logDependencyGraph() {
        log.debug("=== Dependency Graph Details ===");
        
        // Log raw rate dependencies
        log.debug("Raw rate dependencies:");
        directRawDependencies.forEach((rawSymbol, dependentRules) -> {
            String rulesList = dependentRules.stream()
                .map(r -> r.getOutputSymbol() + " (priority: " + r.getPriority() + ")")
                .collect(Collectors.joining(", "));
            log.debug("  {} triggers: {}", rawSymbol, rulesList);
        });
        
        // Log calculated rate dependencies
        log.debug("Calculated rate dependencies:");
        directCalculatedDependencies.forEach((calcSymbol, dependentRules) -> {
            String rulesList = dependentRules.stream()
                .map(r -> r.getOutputSymbol() + " (priority: " + r.getPriority() + ")")
                .collect(Collectors.joining(", "));
            log.debug("  {} triggers: {}", calcSymbol, rulesList);
        });
        
        log.debug("================================");
    }

    public List<CalculationRuleDto> getCalculationsToTrigger(String updatedSymbol, boolean isRawRateUpdate) {
        Set<CalculationRuleDto> triggeredRules = new HashSet<>();
        String normalizedSymbol = updatedSymbol;
        
        String withoutPrefix = updatedSymbol.startsWith("calc_rate:") ? 
            updatedSymbol.substring("calc_rate:".length()) : updatedSymbol;
        
        String withSlash = com.toyota.mainapp.util.SymbolUtils.formatWithSlash(withoutPrefix);
        String withoutSlash = com.toyota.mainapp.util.SymbolUtils.removeSlash(withoutPrefix);
        
        Map<String, List<CalculationRuleDto>> dependencyMap = 
            isRawRateUpdate ? directRawDependencies : directCalculatedDependencies;
            
        // Check all possible formats
        for (String symbolVariant : Arrays.asList(updatedSymbol, withoutPrefix, withSlash, withoutSlash)) {
            List<CalculationRuleDto> rules = dependencyMap.getOrDefault(symbolVariant, Collections.emptyList());
            triggeredRules.addAll(rules);
        }
        
        // Also check for _AVG variants
        if (updatedSymbol.endsWith("_AVG")) {
            String baseSymbol = updatedSymbol.substring(0, updatedSymbol.length() - 4);
            List<CalculationRuleDto> rules = dependencyMap.getOrDefault(baseSymbol, Collections.emptyList());
            triggeredRules.addAll(rules);
        }

        if (!triggeredRules.isEmpty()) {
            log.info("Found {} unique rules triggered by {}: {}", 
                triggeredRules.size(), 
                updatedSymbol,
                triggeredRules.stream().map(CalculationRuleDto::getOutputSymbol).collect(Collectors.joining(", ")));
        }

        return triggeredRules.stream()
                .sorted(Comparator.comparingInt(CalculationRuleDto::getPriority))
                .collect(Collectors.toList());
    }

    public List<CalculationRuleDto> getAllRulesSortedByPriority() {
        return new ArrayList<>(allRulesSortedByPriority); // Return a copy
    }
}
