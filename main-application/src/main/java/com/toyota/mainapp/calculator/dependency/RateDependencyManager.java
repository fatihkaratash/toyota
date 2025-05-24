package com.toyota.mainapp.calculator.dependency;

import com.toyota.mainapp.dto.config.CalculationRuleDto; // ADDED new import
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

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

        this.allRulesSortedByPriority.addAll(rules); // Assuming rules are already sorted

        for (CalculationRuleDto rule : rules) {
            if (rule.getDependsOnRaw() != null) {
                for (String rawSymbolKey : rule.getDependsOnRaw()) {
                    directRawDependencies.computeIfAbsent(rawSymbolKey, k -> new ArrayList<>()).add(rule);
                }
            }
            if (rule.getDependsOnCalculated() != null) {
                for (String calculatedSymbolKey : rule.getDependsOnCalculated()) {
                    directCalculatedDependencies.computeIfAbsent(calculatedSymbolKey, k -> new ArrayList<>()).add(rule);
                }
            }
        }
        log.info("Built dependency graph. Raw dependencies: {}, Calculated dependencies: {}. Total rules: {}",
                directRawDependencies.size(), directCalculatedDependencies.size(), allRulesSortedByPriority.size());
        log.debug("Direct Raw Dependencies: {}", directRawDependencies);
        log.debug("Direct Calculated Dependencies: {}", directCalculatedDependencies);
    }

    /**
     * Gets a list of calculation rules that should be triggered by an update to a specific symbol.
     * The returned list of rules maintains their relative priority.
     * @param updatedSymbol The symbol of the rate that was updated (e.g., "PROVIDER_SYMBOL" for raw, "CALC_SYMBOL" for calculated).
     * @param isRawRateUpdate True if the updated symbol is for a raw rate, false for a calculated rate.
     * @return A list of CalculationRuleDto to trigger, sorted by priority.
     */
    public List<CalculationRuleDto> getCalculationsToTrigger(String updatedSymbol, boolean isRawRateUpdate) {
        List<CalculationRuleDto> triggeredRules;
        if (isRawRateUpdate) {
            triggeredRules = directRawDependencies.getOrDefault(updatedSymbol, Collections.emptyList());
        } else {
            triggeredRules = directCalculatedDependencies.getOrDefault(updatedSymbol, Collections.emptyList());
        }

        if (triggeredRules.isEmpty()) {
            return Collections.emptyList();
        }

        // The rules within the retrieved list are already part of the globally sorted list.
        // To ensure they are processed in their correct relative order if multiple are triggered,
        // we sort this sub-list by priority.
        return triggeredRules.stream()
                .sorted(java.util.Comparator.comparingInt(CalculationRuleDto::getPriority))
                .collect(Collectors.toList());
    }

    public List<CalculationRuleDto> getAllRulesSortedByPriority() {
        return new ArrayList<>(allRulesSortedByPriority); // Return a copy
    }
}
