package com.toyota.restserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Toyota Financial Data Platform - REST Rate Provider Application
 * 
 * Main application entry point for the REST-based financial rate data provider.
 * Provides authenticated HTTP endpoints for real-time rate data retrieval
 * with dynamic rate simulation capabilities for the Toyota platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@SpringBootApplication
public class RestRateProviderApplication {

    private static final Logger logger = LoggerFactory.getLogger(RestRateProviderApplication.class);

    public static void main(String[] args) {
        logger.info("Starting REST Rate Provider Application with Basic Authentication...");
        SpringApplication.run(RestRateProviderApplication.class, args);
        logger.info("REST Rate Provider Application started successfully");
    }

}
