package com.toyota.mainapp.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * LoggingHelper - Utility class for standardized logging.
 * This is a simplified version. In a real application, this might involve MDC or more structured logging.
 */
public class LoggingHelper {

    // Operation types
    public static final String OPERATION_SUBSCRIBE = "SUBSCRIBE";
    public static final String OPERATION_UNSUBSCRIBE = "UNSUBSCRIBE";
    public static final String OPERATION_UPDATE = "UPDATE";
    public static final String OPERATION_CONNECT = "CONNECT";
    public static final String OPERATION_DISCONNECT = "DISCONNECT";
    public static final String OPERATION_ERROR = "ERROR";
    public static final String OPERATION_ALERT = "ALERT";
    public static final String OPERATION_INFO = "INFO";
    public static final String OPERATION_START = "START";
    public static final String OPERATION_STOP = "STOP";
    public static final String OPERATION_VALIDATE = "VALIDATE";
    public static final String OPERATION_CACHE = "CACHE";
    public static final String OPERATION_CALCULATE = "CALCULATE";
    public static final String OPERATION_PUBLISH = "PUBLISH";

    // Platforms/Components
    public static final String COMPONENT_MAIN_APP = "MAIN_APP";
    public static final String COMPONENT_COORDINATOR = "COORDINATOR";
    public static final String COMPONENT_SUBSCRIBER = "SUBSCRIBER";
    public static final String COMPONENT_VALIDATOR = "VALIDATOR";
    public static final String COMPONENT_CACHE = "CACHE";
    public static final String COMPONENT_CALCULATOR = "CALCULATOR";
    public static final String COMPONENT_KAFKA_PRODUCER = "KAFKA_PRODUCER";


    private final Logger logger;

    public LoggingHelper(Class<?> clazz) {
        this.logger = LogManager.getLogger(clazz);
    }

    private String format(String operation, String component, String subject, String details) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(operation).append("]");
        sb.append("[").append(component).append("]");
        if (subject != null && !subject.isEmpty()) {
            sb.append("[").append(subject).append("]");
        }
        sb.append(" - ").append(details);
        return sb.toString();
    }

    public void info(String operation, String component, String subject, String details) {
        logger.info(format(operation, component, subject, details));
    }
    
    public void info(String operation, String component, String details) {
        logger.info(format(operation, component, null, details));
    }

    public void warn(String operation, String component, String subject, String details) {
        logger.warn(format(operation, component, subject, details));
    }
    
    public void warn(String operation, String component, String details) {
        logger.warn(format(operation, component, null, details));
    }

    public void error(String operation, String component, String subject, String details, Throwable t) {
        logger.error(format(operation, component, subject, details), t);
    }
    
    public void error(String operation, String component, String details, Throwable t) {
        logger.error(format(operation, component, null, details), t);
    }

    public void debug(String operation, String component, String subject, String details) {
        if (logger.isDebugEnabled()) {
            logger.debug(format(operation, component, subject, details));
        }
    }
     public void debug(String operation, String component, String details) {
        if (logger.isDebugEnabled()) {
            logger.debug(format(operation, component, null, details));
        }
    }

    public void trace(String operation, String component, String subject, String details) {
        if (logger.isTraceEnabled()) {
            logger.trace(format(operation, component, subject, details));
        }
    }
    public void trace(String operation, String component, String details) {
        if (logger.isTraceEnabled()) {
            logger.trace(format(operation, component, null, details));
        }
    }
}
