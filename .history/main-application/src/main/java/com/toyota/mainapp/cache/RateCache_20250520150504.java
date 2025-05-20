package com.toyota.mainapp.cache;

import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for caching rates.
 */
public interface RateCache {
    
    /**
     * Caches a raw rate.
     * 
     * @param rate The rate to cache
     */
    void cacheRawRate(Rate rate);
    
    /**
     * Caches a calculated rate.
     * 
     * @param rate The calculated rate to cache
     */
    void cacheCalculatedRate(CalculatedRate rate);
    
    /**
     * Gets a raw rate.
     * 
     * @param symbol The symbol of the rate
     * @param platformName The platform name
     * @return The rate, or empty if not found
     */
    Optional<Rate> getRawRate(String symbol, String platformName);
    
    /**
     * Gets a calculated rate.
     * 
     * @param symbol The symbol of the calculated rate
     * @return The calculated rate, or empty if not found
     */
    Optional<CalculatedRate> getCalculatedRate(String symbol);
    
    /**
     * Gets all raw rates with the given symbol prefix.
     * 
     * @param symbolPrefix The symbol prefix
     * @return Map of symbol to rate
     */
    Map<String, Rate> getAllRawRates(String symbolPrefix);
    
    /**
     * Gets all calculated rates.
     * 
     * @return Map of symbol to calculated rate
     */
    Map<String, CalculatedRate> getAllCalculatedRates();
    
    /**
     * Clears all cached rates.
     */
    void clearAll();
    
    /**
     * Checks if the cache is available.
     * 
     * @return true if available, false otherwise
     */
    boolean isAvailable();
}
