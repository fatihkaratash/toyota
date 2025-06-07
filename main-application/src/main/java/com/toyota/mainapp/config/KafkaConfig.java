package com.toyota.mainapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.beans.factory.annotation.Qualifier;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * ✅ MODERNIZED: Kafka configuration using ApplicationProperties
 * Config-driven topics and optimized for real-time pipeline
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConfig {

    // ✅ OPTIONAL: Make ApplicationProperties optional to prevent circular dependency
    private final ApplicationProperties applicationProperties;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.acks:all}")
    private String acks;

    @Value("${app.kafka.topic.partitions:1}")
    private int partitions;

    @Value("${app.kafka.topic.replication:1}")
    private short replicationFactor;

    @Value("${app.kafka.topic.raw-rates}")
    private String rawRatesTopic;

    @Value("${app.kafka.topic.calculated-rates}")
    private String calculatedRatesTopic;

    @Value("${app.kafka.topic.simple-rates}")
    private String simpleRatesTopic;

    @PostConstruct
    public void logConfiguration() {
        log.info("✅ Kafka Configuration:");
        log.info("Bootstrap Servers: {}", bootstrapServers);
        log.info("Acks: {}", acks);
        log.info("Topic Partitions: {}", partitions);
        log.info("Topic Replication Factor: {}", replicationFactor);
        log.info("Raw Rates Topic: {}", rawRatesTopic);
        log.info("Calculated Rates Topic: {}", calculatedRatesTopic);
        log.info("Simple Rates Topic: {}", simpleRatesTopic);
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        log.info("✅ KafkaAdmin configured");
        return new KafkaAdmin(configs);
    }

    /**
     * ✅ REAL-TIME OPTIMIZED: JSON producer factory for individual topics
     */
    @Bean
    public ProducerFactory<String, Object> jsonProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 0); // Real-time için
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        
        log.info("✅ JSON ProducerFactory configured for real-time processing");
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * ✅ STRING OPTIMIZED: String producer factory for batch topics
     */
    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 0); // Real-time için
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        
        log.info("✅ String ProducerFactory configured for batch processing");
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * ✅ JSON KAFKA TEMPLATE: For individual JSON topics
     */
    @Bean
    @Qualifier("jsonKafkaTemplate")
    public KafkaTemplate<String, Object> jsonKafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(jsonProducerFactory());
        log.info("✅ JSON KafkaTemplate configured");
        return template;
    }

    /**
     * ✅ STRING KAFKA TEMPLATE: For batch string topics
     */
    @Bean
    @Qualifier("stringKafkaTemplate")
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(stringProducerFactory());
        log.info("✅ String KafkaTemplate configured");
        return template;
    }

    /**
     * ✅ PRIMARY TEMPLATE: Default JSON template
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return jsonKafkaTemplate();
    }

    /**
     * ✅ TOPIC CREATION: Auto-create topics
     */
    @Bean
    public NewTopic rawRatesTopicBean() {
        NewTopic topic = new NewTopic(rawRatesTopic, partitions, replicationFactor);
        log.info("✅ Raw rates topic configured: {}", rawRatesTopic);
        return topic;
    }

    @Bean
    public NewTopic calculatedRatesTopicBean() {
        NewTopic topic = new NewTopic(calculatedRatesTopic, partitions, replicationFactor);
        log.info("✅ Calculated rates topic configured: {}", calculatedRatesTopic);
        return topic;
    }

    @Bean
    public NewTopic simpleRatesTopicBean() {
        NewTopic topic = new NewTopic(simpleRatesTopic, partitions, replicationFactor);
        log.info("✅ Simple rates topic configured: {}", simpleRatesTopic);
        return topic;
    }
}