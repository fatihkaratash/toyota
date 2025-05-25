package com.toyota.market.trend.redis;

/**
 * Interface for Redis client operations related to market trend settings.
 * This abstraction allows for different Redis client implementations or mocks for testing.
 */
public interface RedisTrendClient {
    
    /**
     * Gets a string value from Redis.
     * 
     * @param key The key to retrieve
     * @return The string value or null if not found
     */
    String getString(String key);
    
    /**
     * Sets a string value in Redis.
     * 
     * @param key The key to set
     * @param value The string value to set
     */
    void setString(String key, String value);
    
    /**
     * Checks if Redis connection is available.
     * 
     * @return true if Redis is accessible, false otherwise
     */
    boolean isAvailable();
}
