package com.toyota.mainapp.kafka.producer.impl;

import com.toyota.mainapp.kafka.message.RateMessage;
import com.toyota.mainapp.kafka.producer.RateProducer;
import com.toyota.mainapp.util.LoggingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class KafkaRateProducer implements RateProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaRateProducer.class);

    private final KafkaTemplate<String, RateMessage> kafkaTemplate;
    private final String defaultRawRatesTopic;
    private final String defaultCalculatedRatesTopic;
    
    // Metrics tracking
    private final ConcurrentMap<String, AtomicLong> messageCountsByTopic = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> errorCountsByTopic = new ConcurrentHashMap<>();

    @Autowired
    public KafkaRateProducer(KafkaTemplate<String, RateMessage> kafkaTemplate,
                             @Qualifier("rawRatesTopicName") String rawRatesTopic,
                             @Qualifier("calculatedRatesTopicName") String calculatedRatesTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.defaultRawRatesTopic = rawRatesTopic;
        this.defaultCalculatedRatesTopic = calculatedRatesTopic;
        
        // Initialize counters
        messageCountsByTopic.put(rawRatesTopic, new AtomicLong(0));
        messageCountsByTopic.put(calculatedRatesTopic, new AtomicLong(0));
        errorCountsByTopic.put(rawRatesTopic, new AtomicLong(0));
        errorCountsByTopic.put(calculatedRatesTopic, new AtomicLong(0));
        
        LoggingHelper.logInitialized(logger, "KafkaRateProducer");
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
        
        // Set correlation ID for tracing this message through logs
        String correlationId = LoggingHelper.setCorrelationId("kafka-" + topic + "-" + message.getSymbol());
        
        try {
            logger.debug("Kafka konusu {} için mesaj gönderiliyor: {}", topic, message);
            CompletableFuture<SendResult<String, RateMessage>> future = kafkaTemplate.send(topic, message.getSymbol(), message);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // Update success metrics
                    getMessageCounter(topic).incrementAndGet();
                    logger.info("Mesaj=[{}] offset=[{}] ile {} konusuna gönderildi",
                            message.getSymbol(), result.getRecordMetadata().offset(), topic);
                } else {
                    // Update error metrics
                    getErrorCounter(topic).incrementAndGet();
                    LoggingHelper.logError(logger, "KafkaRateProducer", 
                            "Mesaj=[" + message.getSymbol() + "] gönderilemedi", ex);
                    // Implement retry or dead-letter queue logic here if necessary
                }
            });
        } catch (Exception e) {
            // Update error metrics
            getErrorCounter(topic).incrementAndGet();
            LoggingHelper.logError(logger, "KafkaRateProducer", 
                    "Kafka konusu " + topic + " için mesaj gönderilirken istisna", e);
        } finally {
            LoggingHelper.clearCorrelationId();
        }
    }
    
    /**
     * Gets the message counter for a specific topic.
     * 
     * @param topic The topic name
     * @return The message counter
     */
    private AtomicLong getMessageCounter(String topic) {
        return messageCountsByTopic.computeIfAbsent(topic, k -> new AtomicLong(0));
    }
    
    /**
     * Gets the error counter for a specific topic.
     * 
     * @param topic The topic name
     * @return The error counter
     */
    private AtomicLong getErrorCounter(String topic) {
        return errorCountsByTopic.computeIfAbsent(topic, k -> new AtomicLong(0));
    }
    
    /**
     * Gets the current message count for a topic.
     * 
     * @param topic The topic name
     * @return The message count
     */
    public long getMessageCount(String topic) {
        AtomicLong counter = messageCountsByTopic.get(topic);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Gets the current error count for a topic.
     * 
     * @param topic The topic name
     * @return The error count
     */
    public long getErrorCount(String topic) {
        AtomicLong counter = errorCountsByTopic.get(topic);
        return counter != null ? counter.get() : 0;
    }
}
