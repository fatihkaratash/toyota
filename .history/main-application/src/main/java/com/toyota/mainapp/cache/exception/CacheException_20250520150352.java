package com.toyota.mainapp.cache.exception;

/**
 * Exception thrown when there is an error with the cache.
 */
public class CacheException extends RuntimeException {
    
    /**
     * Creates a new CacheException with the given message.
     * 
     * @param message The error message
     */
    public CacheException(String message) {
        super(message);
    }
    
    /**
     * Creates a new CacheException with the given message and cause.
     * 
     * @param message The error message
     * @param cause The cause of the error
     */
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
