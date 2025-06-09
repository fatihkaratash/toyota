package com.toyota.consumer.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Toyota Financial Data Platform - Kafka Consumer Configuration
 * 
 * Configures Kafka consumer factories and listener containers for
 * financial rate data consumption. Optimizes batch processing and
 * acknowledgment handling for reliable message processing.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.max-poll-records:50}") // Default to 500 
    private String maxPollRecords;

    @Value("${app.batch.processing.max-poll-records:500}")
    private String batchMaxPollRecords;

    @Value("${app.batch.processing.fetch-max-wait:500}")
    private String fetchMaxWait;


    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords); 
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); 
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true); 
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE); 
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Pipeline-aware batch optimization
        Map<String, Object> batchProps = new HashMap<>();
        batchProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, batchMaxPollRecords);
        batchProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWait);
        
        ConsumerFactory<String, String> batchConsumerFactory = new DefaultKafkaConsumerFactory<>(
            createBatchConsumerProps());
        factory.setConsumerFactory(batchConsumerFactory);
        
        return factory;
    }

    private Map<String, Object> createBatchConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, batchMaxPollRecords);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWait);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }
}