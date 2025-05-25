package com.toyota.market.trend.exception;

/**
 * Exception thrown when there is a problem with trend synchronization.
 */
public class TrendSynchronizationException extends RuntimeException {

    public TrendSynchronizationException(String message) {
        super(message);
    }

    public TrendSynchronizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
