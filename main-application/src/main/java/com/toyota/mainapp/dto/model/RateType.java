package com.toyota.mainapp.dto.model;

/**
 * Toyota Financial Data Platform - Rate Type Enumeration
 * 
 * Defines different types of rate data flowing through the platform
 * including raw provider rates, calculated averages, cross rates,
 * and status information with corresponding event types.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
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
