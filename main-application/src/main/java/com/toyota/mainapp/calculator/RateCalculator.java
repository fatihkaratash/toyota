package com.toyota.mainapp.calculator;

import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;

import java.util.Map;
import java.util.Set;

/**
 * Interface for calculating derived rates from raw rates.
 */
public interface RateCalculator {
    
    /**
     * Determines if a rate update for the specified symbol should trigger calculation
     * of derived rates.
     * 
     * @param symbol The symbol to check
     * @return true if the symbol should trigger calculation, false otherwise
     */
    boolean shouldCalculate(String symbol);
    
    /**
     * Calculates derived rates based on the specified raw rate.
     * 
     * @param rate The raw rate that triggered calculation
     * @return Map of derived rate symbol to calculated rate
     */
    Map<String, CalculatedRate> calculateDerivedRates(Rate rate);
    
    /**
     * Gets the symbols that, when updated, should trigger recalculation
     * of specific derived rates.
     * 
     * @return Map of trigger symbol to set of derived symbols
     */
    Map<String, Set<String>> getTriggerMap();
    
    /**
     * Gets the available calculation strategies.
     * 
     * @return Map of derived symbol to calculation strategy
     */
    Map<String, CalculationStrategy> getCalculationStrategies();
}
