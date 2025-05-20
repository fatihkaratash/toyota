package com.toyota.mainapp.subscriber;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;

import java.util.List;

/**
 * Interface for platform subscribers that receive rate updates.
 * Each implementation should run in its own independent thread.
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
     * Establishes a connection to the data source.
     * This method should be non-blocking and return immediately after initiating the connection process.
     */
    void connect();
    
    /**
     * Disconnects from the data source.
     */
    void disconnect();
    
    /**
     * Subscribes to the specified symbols.
     * 
     * @param symbols List of symbols to subscribe to
     */
    void subscribe(List<String> symbols);
    
    /**
     * Unsubscribes from the specified symbols.
     * 
     * @param symbols List of symbols to unsubscribe from
     */
    void unsubscribe(List<String> symbols);
    
    /**
     * Starts the subscription.
     * This method should initiate the connect sequence if not already connected.
     */
    void startSubscription();
    
    /**
     * Stops the subscription.
     * This method should disconnect if currently connected.
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
