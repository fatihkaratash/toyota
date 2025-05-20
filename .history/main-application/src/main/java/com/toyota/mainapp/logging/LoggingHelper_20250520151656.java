package com.toyota.mainapp.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;
import java.util.UUID;

public final class LoggingHelper {

    private static final String CORRELATION_ID_KEY = "correlationId";

    // Define constants for structured logging if needed (from original file, can be used by callers)
    public static final String OPERATION_START = "OPERATION_START";
    public static final String OPERATION_STOP = "OPERATION_STOP";
    public static final String OPERATION_INFO = "OPERATION_INFO";
    public static final String OPERATION_ALERT = "OPERATION_ALERT";
    public static final String OPERATION_ERROR = "OPERATION_ERROR";

    public static final String COMPONENT_SUBSCRIBER = "SUBSCRIBER";
    // Add other components as needed

    private LoggingHelper() { // Private constructor for utility class
    }

    public static void logStartup(Logger logger, String componentName, String componentType) {
        logger.info("[STARTUP] {} '{}' başlatılıyor.", componentType, componentName);
    }

    public static void logShutdown(Logger logger, String componentName, String componentType) {
        logger.info("[SHUTDOWN] {} '{}' durduruluyor.", componentType, componentName);
    }

    public static void logInitialized(Logger logger, String componentName) {
        logger.info("[INITIALIZED] {} başarıyla başlatıldı.", componentName);
    }

    public static void logError(Logger logger, String componentName, String context, String message, Throwable t) {
        logger.error("[ERROR] {} [{}] - {}: {}", componentName, context, message, t != null ? t.getMessage() : "N/A", t);
    }

    public static void logError(Logger logger, String componentName, String context, String message) {
        logger.error("[ERROR] {} [{}] - {}", componentName, context, message);
    }

    public static void logWarning(Logger logger, String componentName, String context, String message) {
        logger.warn("[WARNING] {} [{}] - {}", componentName, context, message);
    }
    
    public static String setCorrelationId() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        return correlationId;
    }

    public static String setCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(CORRELATION_ID_KEY, correlationId);
        return correlationId;
    }

    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
