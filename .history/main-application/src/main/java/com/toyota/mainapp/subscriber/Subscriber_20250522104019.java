package com.toyota.mainapp.subscriber;

/**
 * Interface for message subscribers that can connect to a message broker,
 * subscribe to topics, and handle incoming messages.
 */
public interface Subscriber {
    /**
     * Start the subscriber and begin listening for messages.
     */
    void start();
    
    /**
     * Stop the subscriber and close all connections.
     */
    void stop();
    
    /**
     * Subscribe to a specific topic/channel.
     * 
     * @param topic the topic name to subscribe to
     */
    void subscribe(String topic);
    
    /**
     * Unsubscribe from a specific topic/channel.
     * 
     * @param topic the topic name to unsubscribe from
     */
    void unsubscribe(String topic);
}
