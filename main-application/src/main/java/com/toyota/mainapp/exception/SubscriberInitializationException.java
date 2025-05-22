package com.toyota.mainapp.exception;

public class SubscriberInitializationException extends RuntimeException {
    public SubscriberInitializationException(String message) {
        super(message);
    }

    public SubscriberInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
