package com.toyota.mainapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.dto.kafka.RateMessageDto;
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

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${app.kafka.topic.raw-rates:financial-raw-rates}")
    private String rawRatesTopicName;

    @Value("${app.kafka.topic.calculated-rates:financial-calculated-rates}")
    private String calculatedRatesTopicName;

    @Value("${app.kafka.topic.simple-rates:financial-simple-rates}") 
    private String simpleRatesTopicName;


    @Value("${app.kafka.topic.partitions:3}")
    private Integer topicPartitions;

    @Value("${app.kafka.topic.replication:1}")
    private Integer topicReplication;

    private final ObjectMapper objectMapper;

    public KafkaConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public Map<String, Object> baseProducerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        log.info("Base Kafka producer properties configured for bootstrap servers: {}", bootstrapServers);
        return props;
    }

    // Updated to use RateMessageDto for all message types
    @Bean
    public ProducerFactory<String, RateMessageDto> rateMessageProducerFactory() {
        Map<String, Object> configProps = new HashMap<>(baseProducerConfigs());
        JsonSerializer<RateMessageDto> valueSerializer = new JsonSerializer<>(objectMapper);
        
        DefaultKafkaProducerFactory<String, RateMessageDto> factory = new DefaultKafkaProducerFactory<>(configProps);
        factory.setValueSerializer(valueSerializer);
        return factory;
    }

    //reefactor sonrası eksik beanler
    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> configProps = new HashMap<>(baseProducerConfigs());
        // Ensure the value serializer is StringSerializer for this factory
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }


    @Bean
    public KafkaTemplate<String, RateMessageDto> rateMessageKafkaTemplate() {
        return new KafkaTemplate<>(rateMessageProducerFactory());
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic rawRatesTopic() {
        log.info("Creating Kafka topic: {}, partitions: {}, replication: {}",
                rawRatesTopicName, topicPartitions, topicReplication);
        return new NewTopic(rawRatesTopicName, topicPartitions, (short) topicReplication.intValue());
    }

    @Bean
    public NewTopic calculatedRatesTopic() {
        log.info("Creating Kafka topic: {}, partitions: {}, replication: {}",
                calculatedRatesTopicName, topicPartitions, topicReplication);
        return new NewTopic(calculatedRatesTopicName, topicPartitions, (short) topicReplication.intValue());
    }

    @Bean
    public NewTopic simpleRatesTopicBean() { // Renamed from statusTopic()
        log.info("Creating Kafka topic: {}, partitions: {}, replication: {}",
                simpleRatesTopicName, topicPartitions, topicReplication);
        return new NewTopic(simpleRatesTopicName, topicPartitions, (short) topicReplication.intValue());
    }


}