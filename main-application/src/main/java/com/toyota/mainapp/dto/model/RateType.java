package com.toyota.mainapp.dto.model;

/**
 * Enum to represent different types of rate data
 */
public enum RateType {
    /**
     * Raw rate data from providers
     */
    RAW("RAW_RATE"),
    
    /**
     * Calculated rate data
     */
    CALCULATED("CALCULATED_RATE"),
    
    /**
     * Status update about rates
     */
    STATUS("RATE_STATUS");
    
    private final String eventType;
    
    RateType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getEventType() {
        return eventType;
    }
}
