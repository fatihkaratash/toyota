package com.toyota.mainapp.coordinator.callback;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.ProviderRateDto;

/**
 * Callback interface for platform events
 */
public interface PlatformCallback {

    void onRateAvailable(String providerName, ProviderRateDto providerRate);
    void onRateUpdate(String providerName, ProviderRateDto rateUpdate);
    void onProviderConnectionStatus(String providerName, boolean isConnected, String statusMessage);
    void onRateStatus(String providerName, BaseRateDto statusRate);
    void onProviderError(String providerName, String errorMessage, Throwable throwable);
}
