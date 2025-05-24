package com.toyota.mainapp.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationRuleDto {
    private String outputSymbol;
    private String description;
    private String strategyType; // e.g., "JAVA_CLASS", "GROOVY_SCRIPT"
    private String implementation; // Fully qualified class name or Spring bean name for JAVA_CLASS, script path for GROOVY_SCRIPT
    private List<String> dependsOnRaw; // List of raw rate symbols (cache keys)
    private List<String> dependsOnCalculated; // List of calculated rate symbols (cache keys)
    private int priority; // Lower value means higher priority
    private Map<String, String> inputParameters; // Additional string-based parameters for the strategy
}