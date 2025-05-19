package com.toyota.mainapp.kafka.producer;

import com.toyota.mainapp.kafka.message.RateMessage;

public interface RateProducer {
    /**
     * Sends a rate message to the configured Kafka topic.
     *
     * @param message The RateMessage to send.
     */
    void sendRate(RateMessage message);

    /**
     * Sends a rate message to a specific Kafka topic.
     *
     * @param topic   The Kafka topic to send the message to.
     * @param message The RateMessage to send.
     */
    void sendRate(String topic, RateMessage message);
}
