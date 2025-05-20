package com.toyota.mainapp.kafka.producer;

import com.toyota.mainapp.kafka.message.RateMessage;

/**
 * Interface for sending rate messages to Kafka.
 */
public interface RateProducer {
    
    /**
     * Sends a rate message to the default topic.
     * 
     * @param message The message to send
     */
    void sendRate(RateMessage message);
    
    /**
     * Sends a rate message to the specified topic.
     * 
     * @param topic The topic to send the message to
     * @param message The message to send
     */
    void sendRate(String topic, RateMessage message);
}
