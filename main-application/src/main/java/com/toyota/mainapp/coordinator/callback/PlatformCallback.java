package com.toyota.mainapp.coordinator.callback;

import com.toyota.mainapp.dto.ProviderRateDto;
import com.toyota.mainapp.dto.RateStatusDto;

public interface PlatformCallback {
    void onProviderConnectionStatus(String providerName, boolean isConnected, String statusMessage);
    void onRateAvailable(String providerName, ProviderRateDto rate);
    void onRateUpdate(String providerName, ProviderRateDto rateUpdate);
    void onRateStatus(String providerName, RateStatusDto rateStatus);
    void onProviderError(String providerName, String errorMessage, Throwable throwable);
}
