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
    private List<String> inputSymbols;
    private String outputSymbol;
    private String description;
    private String strategyType; 
    private String implementation;
    private List<String> dependsOnRaw; // List of raw rate symbols (cache keys)
    private List<String> dependsOnCalculated; // List of calculated rate symbols (cache keys)
    private int priority; 
    private Map<String, String> inputParameters;
}