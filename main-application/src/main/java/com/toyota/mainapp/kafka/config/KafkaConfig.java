package com.toyota.mainapp.kafka.config;

import com.toyota.mainapp.kafka.message.RateMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Value("${app.kafka.topic.raw-rates:raw-rates-topic}")
    private String rawRatesTopic;

    @Value("${app.kafka.topic.calculated-rates:calculated-rates-topic}")
    private String calculatedRatesTopic;

    @Bean
    public ProducerFactory<String, RateMessage> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Add other producer properties if needed (e.g., acks, retries, batch.size)
        // configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        // configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, RateMessage> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public String rawRatesTopicName() {
        return rawRatesTopic;
    }

    @Bean
    public String calculatedRatesTopicName() {
        return calculatedRatesTopic;
    }
}
