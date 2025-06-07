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
    
    // ✅ CONFIG-DRIVEN: Strategy selection
    private String strategyType; // "AVG", "CROSS", "GROOVY"
    private String implementation; // "groovy/average.groovy", "java:ConfigurableAverageStrategy"
    
    private List<String> inputSymbols;
    private Map<String, String> inputParameters;
    
    // ✅ NEW: Strategy-specific configuration
    private Map<String, Object> strategyConfig;
    
    /**
     * ✅ Helper: Determine if this is a Groovy strategy
     */
    public boolean isGroovyStrategy() {
        return implementation != null && 
               (implementation.endsWith(".groovy") || implementation.contains("groovy"));
    }
    
    /**
     * ✅ Helper: Determine if this is a Java strategy
     */
    public boolean isJavaStrategy() {
        return implementation != null && 
               (implementation.startsWith("java:") || implementation.contains("Strategy"));
    }
}