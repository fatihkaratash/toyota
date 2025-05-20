package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.model.Rate;

/**
 * Interface for callback methods to notify the Coordinator about platform events.
 * This is implemented by the MainCoordinator to receive updates from subscribers.
 */
public interface PlatformCallback {
    
    /**
     * Called when a connection is established with a platform.
     * 
     * @param platformName The name of the platform that connected
     */
    void onConnect(String platformName);
    
    /**
     * Called when a connection with a platform is lost or closed.
     * 
     * @param platformName The name of the platform that disconnected
     * @param reason The reason for disconnection
     */
    void onDisconnect(String platformName, String reason);
    
    /**
     * Called when a rate becomes available (first received).
     * 
     * @param rate The rate that became available
     */
    void onRateAvailable(Rate rate);
    
    /**
     * Called when a rate is updated.
     * 
     * @param rate The updated rate
     */
    void onRateUpdate(Rate rate);
    
    /**
     * Called when there's a status change for a rate.
     * 
     * @param symbol The rate symbol
     * @param platformName The platform name
     * @param status The new status
     */
    void onRateStatus(String symbol, String platformName, String status);
    
    /**
     * Called when an error occurs.
     * 
     * @param platformName The platform where the error occurred
     * @param errorMessage The error message
     * @param throwable The exception, if any
     */
    void onError(String platformName, String errorMessage, Throwable throwable);
    
    /**
     * Called when there's a general status change for a platform.
     * 
     * @param platformName The platform name
     * @param statusMessage The status message
     */
    void onStatusChange(String platformName, String statusMessage);
}
