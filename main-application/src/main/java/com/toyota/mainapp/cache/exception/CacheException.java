package com.toyota.mainapp.cache.exception;

/**
 * Exception thrown when cache operations fail.
 */
public class CacheException extends RuntimeException {
    
    public CacheException(String message) {
        super(message);
    }
    
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
