package com.toyota.restserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class RestRateProviderApplication {

    private static final Logger logger = LoggerFactory.getLogger(RestRateProviderApplication.class);

    public static void main(String[] args) {
        logger.info("Starting REST Rate Provider Application with Basic Authentication...");
        SpringApplication.run(RestRateProviderApplication.class, args);
        logger.info("REST Rate Provider Application started successfully");
    }

}
