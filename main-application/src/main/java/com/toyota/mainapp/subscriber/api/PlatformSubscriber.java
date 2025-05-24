package com.toyota.mainapp.subscriber.api;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;
import com.toyota.mainapp.exception.ConnectionAttemptException;

import java.util.List;

/**
 * Interface for platform subscribers
 */
public interface PlatformSubscriber {
    /**
     * Connect to the data provider
     * @throws Exception if connection fails
     */
    void connect() throws Exception;

    /**
     * Check if connected to provider
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Disconnect from the data provider
     */
    void disconnect();

    /**
     * Start the main processing loop
     */
    void startMainLoop();

    /**
     * Stop the main processing loop
     */
    void stopMainLoop();

    /**
     * Get the provider name
     * @return provider name
     */
    String getProviderName();

    void init(SubscriberConfigDto config, PlatformCallback callback);



}
