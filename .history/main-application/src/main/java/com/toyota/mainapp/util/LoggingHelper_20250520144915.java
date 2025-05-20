package com.toyota.mainapp.util;

import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Helper class for standardized logging across the application.
 * Provides utility methods for consistent log level usage and message formatting.
 */
public class LoggingHelper {
    
    private static final String REQUEST_ID_KEY = "requestId";
    
    /**
     * Sets a correlation ID in the MDC for tracking a request through the system.
     * If no ID is provided, a new UUID is generated.
     * 
     * @param correlationId Optional correlation ID
     * @return The correlation ID that was set
     */
    public static String setCorrelationId(String correlationId) {
        String actualId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        MDC.put(REQUEST_ID_KEY, actualId);
        return actualId;
    }
    
    /**
     * Clears the correlation ID from MDC.
     * Should be called at the end of request processing.
     */
    public static void clearCorrelationId() {
        MDC.remove(REQUEST_ID_KEY);
    }
    
    /**
     * Gets the current correlation ID from MDC.
     * 
     * @return Current correlation ID or null if not set
     */
    public static String getCorrelationId() {
        return MDC.get(REQUEST_ID_KEY);
    }
    
    /**
     * Logs startup information for a component.
     * 
     * @param logger The logger to use
     * @param componentName Name of the component
     */
    public static void logStartup(Logger logger, String componentName) {
        logger.info("🚀 {} başlatılıyor...", componentName);
    }
    
    /**
     * Logs shutdown information for a component.
     * 
     * @param logger The logger to use
     * @param componentName Name of the component
     */
    public static void logShutdown(Logger logger, String componentName) {
        logger.info("🛑 {} durduruluyor...", componentName);
    }
    
    /**
     * Logs successful initialization of a component.
     * 
     * @param logger The logger to use
     * @param componentName Name of the component
     */
    public static void logInitialized(Logger logger, String componentName) {
        logger.info("✅ {} başarıyla başlatıldı", componentName);
    }
    
    /**
     * Logs an error with a standardized format.
     * 
     * @param logger The logger to use
     * @param componentName Name of the component
     * @param message Error message
     * @param throwable Exception that occurred
     */
    public static void logError(Logger logger, String componentName, String message, Throwable throwable) {
        logger.error("❌ {} - {}: {}", componentName, message, throwable.getMessage(), throwable);
    }
    
    /**
     * Logs a warning with a standardized format.
     * 
     * @param logger The logger to use
     * @param componentName Name of the component
     * @param message Warning message
     */
    public static void logWarning(Logger logger, String componentName, String message) {
        logger.warn("⚠️ {} - {}", componentName, message);
    }
    
    /**
     * Logs info about data processing.
     * 
     * @param logger The logger to use
     * @param dataType Type of data being processed
     * @param identifier Identifier for the data
     * @param status Status of processing
     */
    public static void logDataProcessing(Logger logger, String dataType, String identifier, String status) {
        logger.info("📊 {} işleniyor '{}': {}", dataType, identifier, status);
    }
}
