package com.toyota.mainapp.validation;

import com.toyota.mainapp.model.Rate;

import java.util.List;
import java.util.Optional;

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

    /**
     * Gets a specific validation rule by its name.
     *
     * @param ruleName The name of the rule to retrieve.
     * @return An Optional containing the rule if found, or an empty Optional otherwise.
     */
    Optional<ValidationRule> getRule(String ruleName);

    /**
     * Gets all currently active validation rules.
     *
     * @return A list of all validation rules.
     */
    List<ValidationRule> getAllRules();
}
