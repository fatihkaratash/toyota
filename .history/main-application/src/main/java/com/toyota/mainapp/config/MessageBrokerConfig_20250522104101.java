package com.toyota.mainapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

/**
 * Configuration for the message broker connection settings.
 */
@Configuration
public class MessageBrokerConfig {
    
    @Value("${message.broker.host:localhost}")
    private String brokerHost;
    
    @Value("${message.broker.port:1883}")
    private int brokerPort;
    
    @Value("${message.broker.username:}")
    private String username;
    
    @Value("${message.broker.password:}")
    private String password;
    
    @Bean
    public String brokerUrl() {
        return "tcp://" + brokerHost + ":" + brokerPort;
    }
    
    // Additional beans for configuring the specific message broker client
    // would be defined here (e.g., Kafka consumer, MQTT client, etc.)
}
