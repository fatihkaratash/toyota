package com.toyota.mainapp.subscriber;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;

import java.util.List;

public interface PlatformSubscriber {
    /**
     * Initializes the subscriber with its configuration and the callback mechanism.
     * This method should be called before startSubscription.
     * @param definition The configuration for this subscriber.
     * @param callback The callback to send data and errors to the coordinator.
     */
    void initialize(SubscriberDefinition definition, PlatformCallback callback);

    /**
     * Starts the subscription process (e.g., connects to TCP server, starts polling REST endpoint).
     */
    void startSubscription();

    /**
     * Stops the subscription process.
     */
    void stopSubscription();

    /**
     * @return The name of the platform this subscriber connects to.
     */
    String getPlatformName();

    /**
     * @return The list of symbols this subscriber is configured for.
     */
    List<String> getSubscribedSymbols();

    /**
     * @return true if the subscriber is currently active and connected/polling.
     */
    boolean isActive();
}
