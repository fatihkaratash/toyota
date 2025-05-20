package com.toyota.mainapp.subscriber.dynamic;

import com.toyota.mainapp.subscriber.PlatformSubscriber;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry interface for managing platform subscribers.
 */
public interface SubscriberRegistry {
    
    /**
     * Initializes all subscribers based on configuration.
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
     * Starts a specific subscriber by name.
     * 
     * @param name The name of the subscriber to start
     * @return true if the subscriber was started successfully, false otherwise
     */
    boolean startSubscriber(String name);
    
    /**
     * Stops a specific subscriber by name.
     * 
     * @param name The name of the subscriber to stop
     * @return true if the subscriber was stopped successfully, false otherwise
     */
    boolean stopSubscriber(String name);
    
    /**
     * Gets a subscriber by name.
     * 
     * @param name The name of the subscriber to get
     * @return Optional containing the subscriber if found, empty otherwise
     */
    Optional<PlatformSubscriber> getSubscriber(String name);
    
    /**
     * Gets all registered subscribers.
     * 
     * @return List of all registered subscribers
     */
    List<PlatformSubscriber> getAllSubscribers();
    
    /**
     * Gets a map of subscriber names to their status information.
     * 
     * @return Map of subscriber names to status information
     */
    default Map<String, Map<String, Object>> getSubscriberStatuses() {
        throw new UnsupportedOperationException("getSubscriberStatuses not implemented");
    }
    
    /**
     * Gets subscribers filtered by type.
     * 
     * @param type The type of subscribers to get (e.g., "tcp", "rest")
     * @return List of subscribers of the specified type
     */
    default List<PlatformSubscriber> getSubscribersByType(String type) {
        throw new UnsupportedOperationException("getSubscribersByType not implemented");
    }
}
