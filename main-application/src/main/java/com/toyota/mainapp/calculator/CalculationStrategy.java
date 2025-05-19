package com.toyota.mainapp.calculator;

import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;

import java.util.List;
import java.util.Map;

/**
 * Interface for strategies that calculate derived rates.
 */
public interface CalculationStrategy {
    
    /**
     * Calculates a derived rate based on the provided raw rates.
     * 
     * @param targetSymbol The symbol of the derived rate to calculate
     * @param sourceRates Map of symbol to rate for all required source rates
     * @return The calculated derived rate
     */
    CalculatedRate calculate(String targetSymbol, Map<String, Rate> sourceRates);
    
    /**
     * Gets the source symbols required for calculation.
     * 
     * @return List of symbols required as inputs for calculation
     */
    List<String> getRequiredSourceSymbols();
    
    /**
     * Gets the ID of this calculation strategy.
     * 
     * @return The unique ID of this strategy
     */
    String getStrategyId();
}
