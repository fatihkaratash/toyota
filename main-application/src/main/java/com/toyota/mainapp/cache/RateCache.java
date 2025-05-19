package com.toyota.mainapp.cache;

import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;

import java.util.Map;
import java.util.Optional;

/**
 * Interface defining operations for caching raw and calculated rates.
 */
public interface RateCache {
    
    /**
     * Caches a raw rate received from a platform.
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
     * Retrieves a raw rate for a specific symbol and platform.
     * 
     * @param symbol The rate symbol (e.g., "PF1_USDTRY")
     * @param platformName The platform name providing this rate
     * @return Optional containing the rate if found, empty otherwise
     */
    Optional<Rate> getRawRate(String symbol, String platformName);
    
    /**
     * Retrieves a calculated rate by its symbol.
     * 
     * @param symbol The calculated rate symbol (e.g., "USDTRY", "EURTRY")
     * @return Optional containing the calculated rate if found, empty otherwise
     */
    Optional<CalculatedRate> getCalculatedRate(String symbol);
    
    /**
     * Retrieves all raw rates for a specific symbol across all platforms.
     * 
     * @param symbolPrefix The symbol prefix to match (e.g., "USDTRY" would match "PF1_USDTRY", "PF2_USDTRY")
     * @return Map of platform name to rate for the matching symbol
     */
    Map<String, Rate> getAllRawRates(String symbolPrefix);
    
    /**
     * Retrieves all calculated rates.
     * 
     * @return Map of symbol to calculated rate
     */
    Map<String, CalculatedRate> getAllCalculatedRates();
    
    /**
     * Clears all cached rates, both raw and calculated.
     */
    void clearAll();
    
    /**
     * @return true if the cache is available and operational
     */
    boolean isAvailable();
}
