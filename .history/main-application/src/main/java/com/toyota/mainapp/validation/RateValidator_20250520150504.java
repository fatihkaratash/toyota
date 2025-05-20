package com.toyota.mainapp.validation;

import com.toyota.mainapp.model.Rate;

/**
 * Interface for validating rates.
 */
public interface RateValidator {
    
    /**
     * Validates a rate.
     * 
     * @param rate The rate to validate
     * @return true if the rate is valid, false otherwise
     */
    boolean validate(Rate rate);
    
    /**
     * Adds a validation rule.
     * 
     * @param rule The rule to add
     */
    void addRule(ValidationRule rule);
    
    /**
     * Removes a validation rule.
     * 
     * @param ruleName The name of the rule to remove
     * @return true if the rule was removed, false otherwise
     */
    boolean removeRule(String ruleName);
}
