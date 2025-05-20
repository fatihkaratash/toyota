package com.toyota.mainapp.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingHelper {

    private final Logger logger;

    // Define constants for structured logging if needed
    public static final String OPERATION_START = "OPERATION_START";
    public static final String OPERATION_STOP = "OPERATION_STOP";
    public static final String OPERATION_INFO = "OPERATION_INFO";
    public static final String OPERATION_ALERT = "OPERATION_ALERT";
    public static final String OPERATION_ERROR = "OPERATION_ERROR";

    public static final String COMPONENT_SUBSCRIBER = "SUBSCRIBER";
    // Add other components as needed

    public LoggingHelper(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    // Example of structured logging methods
    // The original code calls logger.info(String, String, String, String...)
    // This suggests the logger instance itself is used directly from SLF4J/Log4j
    // So, this class might just be a holder for constants, or it might have helper methods.
    // Given the usage: logger.info(LoggingHelper.OPERATION_START, LoggingHelper.COMPONENT_SUBSCRIBER, "message");
    // it seems the constants are passed to standard SLF4J methods.
    // The DefaultSubscriberRegistry uses its own SLF4J logger instance but passes LoggingHelper constants.
    // Let's adjust based on DefaultSubscriberRegistry:
    // It has: private static final LoggingHelper logger = new LoggingHelper(DefaultSubscriberRegistry.class);
    // And calls: logger.info(LoggingHelper.OPERATION_START, LoggingHelper.COMPONENT_SUBSCRIBER, definition.getName(), "Abone başarıyla başlatıldı: " + definition.getName());
    // This means LoggingHelper needs to provide actual logging methods that take these constants.

    public void info(String operation, String component, String message) {
        logger.info("[{}] [{}] {}", operation, component, message);
    }

    public void info(String operation, String component, String entity, String message) {
        logger.info("[{}] [{}] [{}] {}", operation, component, entity, message);
    }

    public void warn(String operation, String component, String message) {
        logger.warn("[{}] [{}] {}", operation, component, message);
    }
    
    public void warn(String operation, String component, String entity, String message) {
        logger.warn("[{}] [{}] [{}] {}", operation, component, entity, message);
    }

    public void error(String operation, String component, String entity, String message, Throwable t) {
        logger.error("[{}] [{}] [{}] {} - Exception: {}", operation, component, entity, message, t.getMessage(), t);
    }
    
    public void error(String operation, String component, String entity, String message) {
        logger.error("[{}] [{}] [{}] {}", operation, component, entity, message);
    }
}
