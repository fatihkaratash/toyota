package com.toyota.restserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Toyota Financial Data Platform - Application Configuration
 * 
 * Spring Boot configuration for REST rate provider application beans.
 * Defines RestTemplate, ObjectMapper, and thread pool executor beans
 * for HTTP communication and asynchronous processing.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Configuration
public class AppConfig {

    /**
     * HTTP istekleri icin RestTemplate bean'i
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * JSON serialestirme/deserilestirme icin yapilandirilmis ObjectMapper
     */
   /*/ @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }*/

    /**
     * Asenkron islemler icin thread pool executor
     * Gerektiginde asenkron islemler icin kullanilabilir
     */
    @Bean
    public Executor taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor(); // Java 21 virtual threads
    }
}
