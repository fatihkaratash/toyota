package com.toyota.mainapp.model;

/**
 * Enum representing the status of a rate.
 */
public enum RateStatus {
    /**
     * The rate is active and valid.
     */
    ACTIVE,
    
    /**
     * The rate is stale (older than a configured threshold).
     */
    STALE,
    
    /**
     * The rate has been manually overridden.
     */
    OVERRIDDEN,
    
    /**
     * The rate is invalid or has failed validation.
     */
    INVALID,
    
    /**
     * The rate is temporarily unavailable.
     */
    UNAVAILABLE
}
