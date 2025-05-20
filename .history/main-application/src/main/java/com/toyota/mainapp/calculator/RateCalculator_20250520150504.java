package com.toyota.mainapp.calculator;

import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;

import java.util.Map;
import java.util.Set;

/**
 * Interface for calculating derived rates from source rates.
 */
public interface RateCalculator {
    
    /**
     * Determines if a rate update for the given symbol should trigger calculations.
     * 
     * @param symbol Symbol of the updated rate
     * @return true if calculations should be triggered, false otherwise
     */
    boolean shouldCalculate(String symbol);
    
    /**
     * Calculates derived rates based on the updated rate.
     * 
     * @param triggerRate The updated rate that triggered the calculation
     * @return Map of derived symbols to their calculated rates
     */
    Map<String, CalculatedRate> calculateDerivedRates(Rate triggerRate);
    
    /**
     * Gets the mapping from trigger symbols to the set of derived symbols that depend on them.
     * 
     * @return Map of trigger symbols to derived symbols
     */
    Map<String, Set<String>> getTriggerMap();
    
    /**
     * Gets all registered calculation strategies.
     * 
     * @return Map of derived symbols to their calculation strategies
     */
    Map<String, CalculationStrategy> getCalculationStrategies();
}
