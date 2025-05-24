package com.toyota.mainapp.coordinator.callback;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.ProviderRateDto;

/**
 * Callback interface for platform events
 */
public interface PlatformCallback {

    /**
     * Called when a new rate is available
     * 
     * @param providerName The name of the provider
     * @param providerRate The provider rate data
     */
    void onRateAvailable(String providerName, ProviderRateDto providerRate);

    /**
     * Called when a rate is updated
     * 
     * @param providerName The name of the provider
     * @param rateUpdate The updated rate data
     */
    void onRateUpdate(String providerName, ProviderRateDto rateUpdate);

    /**
     * Called when provider connection status changes
     * 
     * @param providerName The name of the provider
     * @param isConnected Whether the provider is connected
     * @param statusMessage Status message
     */
    void onProviderConnectionStatus(String providerName, boolean isConnected, String statusMessage);

    /**
     * Called for status updates about rates
     * 
     * @param providerName The name of the provider
     * @param statusRate The rate status information as BaseRateDto with RateType.STATUS
     */
    void onRateStatus(String providerName, BaseRateDto statusRate);

    /**
     * Called when an error occurs with a provider
     * 
     * @param providerName The name of the provider
     * @param errorMessage Error message
     * @param throwable The exception that occurred (if any)
     */
    void onProviderError(String providerName, String errorMessage, Throwable throwable);
}
