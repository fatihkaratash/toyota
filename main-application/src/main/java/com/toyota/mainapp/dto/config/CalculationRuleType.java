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
}
