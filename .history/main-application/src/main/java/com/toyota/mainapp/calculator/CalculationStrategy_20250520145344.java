package com.toyota.mainapp.calculator;

import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for rate calculation algorithms.
 * Each implementation provides a specific formula for calculating a derived rate.
 */
public interface CalculationStrategy {
    
    /**
     * Gets the unique identifier for this strategy, typically the derived symbol (e.g., "EURTRY").
     * 
     * @return The strategy ID
     */
    String getStrategyId();
    
    /**
     * Gets the list of source rate symbols required for this calculation.
     * For example, to calculate EURTRY, we might need "PF1_USDTRY", "PF2_USDTRY", "PF1_EURUSD", "PF2_EURUSD".
     * 
     * @return List of required source symbols
     */
    List<String> getRequiredSourceSymbols();
    
    /**
     * Calculates a derived rate based on the provided source rates.
     * 
     * @param targetSymbol The symbol for the calculated rate (e.g., "EURTRY")
     * @param sourceRates Map of source rate symbols to their Rate objects
     * @return The calculated rate, or null if calculation failed
     */
    CalculatedRate calculate(String targetSymbol, Map<String, Rate> sourceRates);
}
