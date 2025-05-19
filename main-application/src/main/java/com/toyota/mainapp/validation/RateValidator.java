package com.toyota.mainapp.validation;

import com.toyota.mainapp.model.Rate;

/**
 * Interface for validating incoming rates.
 */
public interface RateValidator {
    
    /**
     * Validates a rate against all configured validation rules.
     * 
     * @param rate The rate to validate
     * @return true if the rate passes all validation rules, false otherwise
     */
    boolean validate(Rate rate);
    
    /**
     * Adds a validation rule to be applied during rate validation.
     * 
     * @param rule The rule to add
     */
    void addRule(ValidationRule rule);
    
    /**
     * Removes a validation rule from the validator.
     * 
     * @param ruleName The name of the rule to remove
     * @return true if the rule was found and removed, false otherwise
     */
    boolean removeRule(String ruleName);
}
