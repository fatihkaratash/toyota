package com.toyota.mainapp.subscriber.api;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;

/**
 * Interface for platform subscribers
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
