package com.toyota.mainapp.kafka.producer.impl;

import com.toyota.mainapp.kafka.message.RateMessage;
import com.toyota.mainapp.kafka.producer.RateProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaRateProducer implements RateProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaRateProducer.class);

    private final KafkaTemplate<String, RateMessage> kafkaTemplate;
    private final String defaultRawRatesTopic;
    private final String defaultCalculatedRatesTopic;


    @Autowired
    public KafkaRateProducer(KafkaTemplate<String, RateMessage> kafkaTemplate,
                             @Qualifier("rawRatesTopicName") String rawRatesTopic,
                             @Qualifier("calculatedRatesTopicName") String calculatedRatesTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.defaultRawRatesTopic = rawRatesTopic;
        this.defaultCalculatedRatesTopic = calculatedRatesTopic;
    }

    @Override
    public void sendRate(RateMessage message) {
        // Determine topic based on message content or a default
        // For simplicity, assuming raw rates go to raw topic, calculated to calculated.
        // This logic might need refinement based on how to distinguish.
        // Let's assume for now that the coordinator will call sendRate(topic, message).
        // If a generic sendRate is called, it might default to rawRatesTopic or throw an error.
        // For this example, let's use a convention: if symbol contains "CALCULATED" or similar,
        // it goes to calculated topic. This is a placeholder for better logic.
        String topic = defaultRawRatesTopic; // Default to raw rates topic
        if (message.getSymbol() != null && message.getSymbol().startsWith("CALCULATED_")) { // Example convention
             topic = defaultCalculatedRatesTopic;
        } else if (message.getSymbol() != null && (message.getSymbol().equals("EURTRY") || message.getSymbol().equals("GBPTRY"))){
            // This is a simple check, in a real scenario, the Rate object type (Rate vs CalculatedRate)
            // would be a better differentiator before it's converted to RateMessage.
            // The MainCoordinator should ideally call sendRate(topic, message)
            topic = defaultCalculatedRatesTopic;
        }
        sendRate(topic, message);
    }

    @Override
    public void sendRate(String topic, RateMessage message) {
        if (message == null) {
            logger.warn("Kafka konusu {} için null mesaj gönderilmeye çalışıldı", topic);
            return;
        }
        try {
            logger.debug("Kafka konusu {} için mesaj gönderiliyor: {}", topic, message);
            CompletableFuture<SendResult<String, RateMessage>> future = kafkaTemplate.send(topic, message.getSymbol(), message);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.info("Mesaj=[{}] offset=[{}] ile {} konusuna gönderildi",
                            message.getSymbol(), result.getRecordMetadata().offset(), topic);
                } else {
                    logger.error("Mesaj=[{}] gönderilemedi, neden: {}",
                            message.getSymbol(), ex.getMessage(), ex);
                    // Implement retry or dead-letter queue logic here if necessary
                }
            });
        } catch (Exception e) {
            logger.error("Kafka konusu {} için mesaj gönderilirken istisna: {}", topic, e.getMessage(), e);
        }
    }
}
