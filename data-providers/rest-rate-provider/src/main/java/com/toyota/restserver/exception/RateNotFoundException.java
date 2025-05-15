package com.toyota.restserver.exception;

public class RateNotFoundException extends RuntimeException {
    public RateNotFoundException(String message) {
        super(message);
    }
}
