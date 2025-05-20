package com.toyota.mainapp.subscriber.dynamic;

import com.toyota.mainapp.subscriber.PlatformSubscriber;
import java.util.List;
import java.util.Optional;

/**
 * Registry for platform subscribers.
 */
public interface SubscriberRegistry {
    
    /**
     * Initializes subscribers from configuration.
     */
    void initializeSubscribers();
    
    /**
     * Starts all registered subscribers.
     */
    void startAllSubscribers();
    
    /**
     * Stops all registered subscribers.
     */
    void stopAllSubscribers();
    
    /**
     * Starts a specific subscriber.
     * 
     * @param name The name of the subscriber
     * @return true if the subscriber was started successfully, false otherwise
     */
    boolean startSubscriber(String name);
    
    /**
     * Stops a specific subscriber.
     * 
     * @param name The name of the subscriber
     * @return true if the subscriber was stopped successfully, false otherwise
     */
    boolean stopSubscriber(String name);
    
    /**
     * Gets a specific subscriber.
     * 
     * @param name The name of the subscriber
     * @return The subscriber, or empty if not found
     */
    Optional<PlatformSubscriber> getSubscriber(String name);
    
    /**
     * Gets all registered subscribers.
     * 
     * @return List of all subscribers
     */
    List<PlatformSubscriber> getAllSubscribers();
}
