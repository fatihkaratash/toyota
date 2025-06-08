package com.toyota.mainapp.dto.config;

public enum CalculationRuleType {
    

    AVG("AVG", "Average calculation"),
    CROSS("CROSS", "Cross rate calculation"),
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

    public static boolean isValidType(String typeString) {
        return fromString(typeString) != null;
    }
    
    public boolean supportsImmediateProcessing() {
        // All current types support immediate processing
        return this == AVG || this == CROSS;
    }

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

    public boolean requiresCacheDependencies() {
        // CROSS rates typically need previously calculated rates
        return this == CROSS;
    }
}
