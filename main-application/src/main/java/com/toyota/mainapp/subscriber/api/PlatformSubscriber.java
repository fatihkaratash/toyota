package com.toyota.mainapp.subscriber.api;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;

/**
 * Toyota Financial Data Platform - Platform Subscriber Interface
 * 
 * Core contract for all rate data subscribers supporting multiple protocols
 * (REST, TCP). Defines lifecycle management, connection handling, and
 * configuration-driven initialization for the financial data platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
public interface PlatformSubscriber {
    void init(SubscriberConfigDto config, PlatformCallback callback);
    void connect();
    void disconnect();
    void startMainLoop();
    void stopMainLoop();
    boolean isConnected();
    String getProviderName();
}
