package com.toyota.mainapp.dto.config;

/**
 * ✅ TYPE SAFETY: Enum for calculation rule types
 * Prevents string matching errors and provides compile-time validation
 */
public enum CalculationRuleType {
    
    /**
     * Average calculation rule type
     * Calculates weighted average from multiple provider rates
     */
    AVG("AVG", "Average calculation"),
    
    /**
     * Cross rate calculation rule type  
     * Calculates cross rates from base currency pairs
     */
    CROSS("CROSS", "Cross rate calculation"),
    
    /**
     * Custom calculation rule type
     * For future extensibility
     */
    CUSTOM("CUSTOM", "Custom calculation");
    
    private final String code;
    private final String description;
    
    CalculationRuleType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * ✅ CONVERSION: From string to enum with fallback
     */
    public static CalculationRuleType fromString(String typeString) {
        if (typeString == null || typeString.trim().isEmpty()) {
            return null;
        }
        
        for (CalculationRuleType type : values()) {
            if (type.code.equalsIgnoreCase(typeString.trim())) {
                return type;
            }
        }
        
        return null;
    }
    
    /**
     * ✅ VALIDATION: Check if string is valid type
     */
    public static boolean isValidType(String typeString) {
        return fromString(typeString) != null;
    }
    
    /**
     * ✅ NEW: Check if this type supports immediate processing
     * ✅ ACTIVELY USED: Stage filtering in immediate pipeline
     * Usage: supportsImmediateProcessing() in stage execution
     */
    public boolean supportsImmediateProcessing() {
        // All current types support immediate processing
        return this == AVG || this == CROSS;
    }

    /**
     * ✅ NEW: Get processing priority for immediate pipeline
     * ✅ ACTIVELY USED: Stage execution ordering
     * Usage: getProcessingPriority() for stage sequence
     */
    public int getProcessingPriority() {
        switch (this) {
            case AVG:
                return 1; // Process averages first
            case CROSS:
                return 2; // Process cross rates after averages
            case CUSTOM:
                return 3; // Process custom calculations last
            default:
                return 10; // Unknown types last
        }
    }

    /**
     * ✅ NEW: Check if type requires cache dependencies
     * ✅ ACTIVELY USED: Dependency management in stages
     * Usage: requiresCacheDependencies() for input collection
     */
    public boolean requiresCacheDependencies() {
        // CROSS rates typically need previously calculated rates
        return this == CROSS;
    }
}
