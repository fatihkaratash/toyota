package com.toyota.mainapp.validation;

import com.toyota.mainapp.model.Rate;

/**
 * Interface for rate validation rules.
 */
public interface ValidationRule {
    
    /**
     * Validates a rate.
     * 
     * @param rate The rate to validate
     * @return true if the rate is valid, false otherwise
     */
    boolean validate(Rate rate);
    
    /**
     * Gets the name of the validation rule.
     * 
     * @return The name of the rule
     */
    String getName();
    
    /**
     * Gets a description of the validation rule.
     * 
     * @return The description of the rule
     */
    String getDescription();
}
