package com.toyota.mainapp.exception;

public class ConnectionAttemptException extends Exception {
    public ConnectionAttemptException(String message) {
        super(message);
    }

    public ConnectionAttemptException(String message, Throwable cause) {
        super(message, cause);
    }
}
