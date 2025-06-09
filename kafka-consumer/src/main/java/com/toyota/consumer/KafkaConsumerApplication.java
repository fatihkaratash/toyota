package com.toyota.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Toyota Financial Data Platform - Kafka Consumer Application
 * 
 * Main application entry point for the financial rate data consumer service.
 * Processes real-time rate snapshots from Kafka topics and persists them
 * to the database for analytics and reporting within the Toyota platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@SpringBootApplication
@EnableScheduling
public class KafkaConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaConsumerApplication.class, args);
    }
}
