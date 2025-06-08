package com.toyota.restserver.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoggingHelper {

    public static final String OPERATION_REQUEST = "REQUEST";
    public static final String OPERATION_RESPONSE = "RESPONSE";
    public static final String OPERATION_LOAD_CONFIG = "LOAD_CONFIG";
    public static final String OPERATION_SIMULATE_RATE = "SIMULATE_RATE";
    public static final String OPERATION_SERVICE_CALL = "SERVICE_CALL";
    public static final String OPERATION_ERROR = "ERROR";
    public static final String OPERATION_INFO = "INFO";
    public static final String OPERATION_START = "START";
    public static final String OPERATION_STOP = "STOP";
    public static final String OPERATION_ALERT = "ALERT";


    public static final String PLATFORM_REST = "REST_PROVIDER";

    private final Logger logger;

    public LoggingHelper(Class<?> clazz) {
        this.logger = LogManager.getLogger(clazz);
    }

    private String formatMessage(String operationType, String platform, String pairName, String details, String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(operationType).append("] ");
        builder.append("[").append(platform).append("] ");
        if (pairName != null && !pairName.isEmpty()) {
            builder.append("[").append(pairName).append("] ");
        }
        if (details != null && !details.isEmpty()) {
            builder.append("[").append(details).append("] ");
        }
        builder.append("- ").append(message);
        return builder.toString();
    }

    public void info(String operationType, String platform, String pairName, String details, String message) {
        logger.info(formatMessage(operationType, platform, pairName, details, message));
    }
    
    public void info(String operationType, String platform, String message) {
        logger.info(formatMessage(operationType, platform, null, null, message));
    }

    public void debug(String operationType, String platform, String pairName, String details, String message) {
        logger.debug(formatMessage(operationType, platform, pairName, details, message));
    }
    
    public void debug(String operationType, String platform, String message) {
        logger.debug(formatMessage(operationType, platform, null, null, message));
    }

    public void warn(String operationType, String platform, String pairName, String details, String message) {
        logger.warn(formatMessage(operationType, platform, pairName, details, message));
    }
    
    public void warn(String operationType, String platform, String message) {
        logger.warn(formatMessage(operationType, platform, null, null, message));
    }

    public void error(String operationType, String platform, String pairName, String details, String message, Throwable t) {
        logger.error(formatMessage(operationType, platform, pairName, details, message), t);
    }
    
    public void error(String operationType, String platform, String message, Throwable t) {
        logger.error(formatMessage(operationType, platform, null, null, message), t);
    }
    
    public void error(String operationType, String platform, String message) {
        logger.error(formatMessage(operationType, platform, null, null, message));
    }
}
