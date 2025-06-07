package com.toyota.mainapp.dto.model;

/**
 * Enum to represent different types of rate data
 */
public enum RateType {

    RAW("RAW_RATE"),
    CALCULATED("CALCULATED_RATE"),
    STATUS("RATE_STATUS"),
    CROSS("CROSS_RATE");
    
    private final String eventType;
    
    RateType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getEventType() {
        return eventType;
    }
}
