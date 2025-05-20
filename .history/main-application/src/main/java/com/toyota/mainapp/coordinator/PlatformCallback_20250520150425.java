package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.model.Rate;

/**
 * Callback interface for platform subscribers to report back to the coordinator.
 */
public interface PlatformCallback {
    
    /**
     * Called when a rate is updated.
     * 
     * @param rate The updated rate
     */
    void onRateUpdate(Rate rate);
    
    /**
     * Called when an error occurs.
     * 
     * @param platformName The name of the platform
     * @param errorMessage The error message
     * @param throwable The throwable, or null if not available
     */
    void onError(String platformName, String errorMessage, Throwable throwable);
    
    /**
     * Called when the status of a platform changes.
     * 
     * @param platformName The name of the platform
     * @param statusMessage The status message
     */
    void onStatusChange(String platformName, String statusMessage);
}
