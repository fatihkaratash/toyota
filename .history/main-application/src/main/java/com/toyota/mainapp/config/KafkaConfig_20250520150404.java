package com.toyota.mainapp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuration for Kafka topics.
 */
@Configuration
public class KafkaConfig {
    
    @Value("${kafka.topic.raw-rates:toyota.raw.rates}")
    private String rawRatesTopic;
    
    @Value("${kafka.topic.calculated-rates:toyota.calculated.rates}")
    private String calculatedRatesTopic;
    
    @Bean
    public NewTopic rawRatesTopic() {
        return TopicBuilder.name(rawRatesTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic calculatedRatesTopic() {
        return TopicBuilder.name(calculatedRatesTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean(name = "rawRatesTopicName")
    public String rawRatesTopicName() {
        return rawRatesTopic;
    }
    
    @Bean(name = "calculatedRatesTopicName")
    public String calculatedRatesTopicName() {
        return calculatedRatesTopic;
    }
}
