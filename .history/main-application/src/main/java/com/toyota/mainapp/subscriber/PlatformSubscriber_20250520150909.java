package com.toyota.mainapp.subscriber;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;

import java.util.List;

/**
 * Interface for platform subscribers that receive rate updates.
 */
public interface PlatformSubscriber {
    
    /**
     * Initializes the subscriber with the given definition and callback.
     * 
     * @param definition The subscriber definition
     * @param callback The callback for reporting updates and errors
     */
    void initialize(SubscriberDefinition definition, PlatformCallback callback);
    
    /**
     * Starts the subscription.
     */
    void startSubscription();
    
    /**
     * Stops the subscription.
     */
    void stopSubscription();
    
    /**
     * Gets the name of the platform.
     * 
     * @return The platform name
     */
    String getPlatformName();
    
    /**
     * Gets the symbols this subscriber is subscribed to.
     * 
     * @return List of subscribed symbols
     */
    List<String> getSubscribedSymbols();
    
    /**
     * Checks if the subscriber is active.
     * 
     * @return true if active, false otherwise
     */
    boolean isActive();
}
