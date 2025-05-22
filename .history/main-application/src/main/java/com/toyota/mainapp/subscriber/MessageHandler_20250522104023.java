package com.toyota.mainapp.subscriber;

/**
 * Interface for handling messages received from subscribed topics.
 */
public interface MessageHandler {
    /**
     * Process a message received from a subscribed topic.
     * 
     * @param topic the topic from which the message was received
     * @param message the message content
     */
    void handleMessage(String topic, String message);
}
