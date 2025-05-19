package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.model.Rate;

public interface PlatformCallback {
    /**
     * Called when a new rate update is received from a platform subscriber.
     * @param rate The rate data.
     */
    void onRateUpdate(Rate rate);

    /**
     * Called when an error occurs within a platform subscriber.
     * @param platformName The name of the platform where the error occurred.
     * @param errorMessage A description of the error.
     * @param throwable The exception, if available.
     */
    void onError(String platformName, String errorMessage, Throwable throwable);

    /**
     * Called when a subscriber's status changes (e.g., connected, disconnected).
     * @param platformName The name of the platform.
     * @param statusMessage A message describing the status change.
     */
    void onStatusChange(String platformName, String statusMessage);
}
