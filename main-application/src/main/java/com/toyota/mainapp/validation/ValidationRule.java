package com.toyota.mainapp.validation;

import com.toyota.mainapp.model.Rate;

/**
 * Interface for validation rules that can be applied to rates.
 */
public interface ValidationRule {
    
    /**
     * Applies this validation rule to a rate.
     * 
     * @param rate The rate to validate
     * @return true if the rate passes this validation rule, false otherwise
     */
    boolean validate(Rate rate);
    
    /**
     * @return The name of this validation rule
     */
    String getName();
    
    /**
     * @return A description of what this validation rule checks for
     */
    String getDescription();
}
