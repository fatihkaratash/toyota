package com.toyota.mainapp.model;

/**
 * Enum representing the status of a rate.
 * 
 * ACTIVE: The rate is currently active and valid.
 * STALE: The rate is outdated but still available.
 * OVERRIDDEN: The rate has been replaced by a new one.
 * INVALID: The rate is not valid for any reason.
 * UNAVAILABLE: The rate is not available for use.
 */
public enum RateStatus {

    ACTIVE,

    STALE,

    OVERRIDDEN,
 
    INVALID,
 
    UNAVAILABLE
}
