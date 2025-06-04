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

    // Key: Raw Rate Symbol (e.g., "PROVIDER_SYMBOL"), Value: List of rules directly dependent on this raw rate
    private final Map<String, List<CalculationRuleDto>> directRawDependencies = new ConcurrentHashMap<>();
    // Key: Calculated Rate Symbol, Value: List of rules directly dependent on this calculated rate
    private final Map<String, List<CalculationRuleDto>> directCalculatedDependencies = new ConcurrentHashMap<>();
    // All rules, sorted by priority (lower value = higher priority)
    private final List<CalculationRuleDto> allRulesSortedByPriority = new CopyOnWriteArrayList<>();

    /**
     * Builds the dependency graph from the loaded calculation rules.
     * Rules should be pre-sorted by priority before calling this method.
     * @param rules A list of CalculationRuleDto, expected to be sorted by priority.
     */
    public void buildDependencyGraph(List<CalculationRuleDto> rules) {
        this.allRulesSortedByPriority.clear();
        this.directRawDependencies.clear();
        this.directCalculatedDependencies.clear();

        if (rules == null || rules.isEmpty()) {
            log.warn("No calculation rules provided to build dependency graph.");
            return;
        }

        // Double-check sort the rules by priority as a safety measure
        List<CalculationRuleDto> sortedRules = new ArrayList<>(rules);
        sortedRules.sort(Comparator.comparing(CalculationRuleDto::getPriority));
        
        // Log the rules in their sorted order to verify
        log.debug("Building dependency graph with rules in priority order:");
        for (CalculationRuleDto rule : sortedRules) {
            log.debug("  Rule: {} (priority: {})", rule.getOutputSymbol(), rule.getPriority());
        }
        
        this.allRulesSortedByPriority.addAll(sortedRules);

        for (CalculationRuleDto rule : sortedRules) {
            if (rule.getDependsOnRaw() != null) {
                for (String rawSymbolKey : rule.getDependsOnRaw()) {
                    // Add rule to directRawDependencies only if it's not already there (based on output symbol)
                    List<CalculationRuleDto> existingRules = directRawDependencies.computeIfAbsent(rawSymbolKey, k -> new ArrayList<>());
                    if (!containsRuleWithSameOutputSymbol(existingRules, rule)) {
                        existingRules.add(rule);
                    }
                }
            }
            if (rule.getDependsOnCalculated() != null) {
                for (String calculatedSymbolKey : rule.getDependsOnCalculated()) {
                    // Add rule to directCalculatedDependencies only if it's not already there (based on output symbol)
                    List<CalculationRuleDto> existingRules = directCalculatedDependencies.computeIfAbsent(calculatedSymbolKey, k -> new ArrayList<>());
                    if (!containsRuleWithSameOutputSymbol(existingRules, rule)) {
                        existingRules.add(rule);
                    }
                }
            }
        }
        log.info("Built dependency graph. Raw dependencies: {}, Calculated dependencies: {}. Total rules: {}",
                directRawDependencies.size(), directCalculatedDependencies.size(), allRulesSortedByPriority.size());
        
        // Add detailed logging of the dependency graph
        logDependencyGraph();
    }
    
    /**
     * Checks if a list of rules already contains a rule with the same output symbol
     */
    private boolean containsRuleWithSameOutputSymbol(List<CalculationRuleDto> rules, CalculationRuleDto newRule) {
        return rules.stream().anyMatch(r -> 
            r.getOutputSymbol().equals(newRule.getOutputSymbol()) || 
            com.toyota.mainapp.util.SymbolUtils.symbolsEquivalent(r.getOutputSymbol(), newRule.getOutputSymbol())
        );
    }
    
    /**
     * Logs detailed information about the dependency graph for debugging
     */
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

    /**
     * Gets a list of calculation rules that should be triggered by an update to a specific symbol.
     * The returned list of rules maintains their relative priority.
     * @param updatedSymbol The symbol of the rate that was updated (e.g., "PROVIDER_SYMBOL" for raw, "CALC_SYMBOL" for calculated).
     * @param isRawRateUpdate True if the updated symbol is for a raw rate, false for a calculated rate.
     * @return A list of CalculationRuleDto to trigger, sorted by priority.
     */
    public List<CalculationRuleDto> getCalculationsToTrigger(String updatedSymbol, boolean isRawRateUpdate) {
        Set<CalculationRuleDto> triggeredRules = new HashSet<>();
        String normalizedSymbol = updatedSymbol;
        
        // For better matching, try both with and without slashes, prefixes, etc.
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
        
        // Log the rules found
        if (!triggeredRules.isEmpty()) {
            log.info("Found {} unique rules triggered by {}: {}", 
                triggeredRules.size(), 
                updatedSymbol,
                triggeredRules.stream().map(CalculationRuleDto::getOutputSymbol).collect(Collectors.joining(", ")));
        }

        // The rules within the retrieved list are already part of the globally sorted list.
        // To ensure they are processed in their correct relative order if multiple are triggered,
        // we sort this sub-list by priority.
        return triggeredRules.stream()
                .sorted(Comparator.comparingInt(CalculationRuleDto::getPriority))
                .collect(Collectors.toList());
    }

    public List<CalculationRuleDto> getAllRulesSortedByPriority() {
        return new ArrayList<>(allRulesSortedByPriority); // Return a copy
    }
}
