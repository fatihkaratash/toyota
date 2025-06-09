package com.toyota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Toyota Financial Data Platform - Kafka Consumer OpenSearch Application
 * 
 * Main application entry point for the financial rate data OpenSearch indexing service.
 * Consumes real-time rate data from multiple Kafka topics and indexes them into
 * OpenSearch for advanced analytics and search capabilities within the Toyota platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@SpringBootApplication
@EnableKafka
public class KafkaConsumerOpensearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaConsumerOpensearchApplication.class, args);
    }
}