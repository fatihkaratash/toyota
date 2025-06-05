package com.toyota.mainapp.subscriber.api;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;

/**
 * Interface for platform subscribers
 */
public interface PlatformSubscriber {

    void connect() throws Exception;
    boolean isConnected();
    void disconnect();
    void startMainLoop();
    void stopMainLoop();
    String getProviderName();
    void init(SubscriberConfigDto config, PlatformCallback callback);
}
