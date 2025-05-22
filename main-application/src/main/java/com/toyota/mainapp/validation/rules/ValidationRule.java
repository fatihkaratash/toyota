package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.NormalizedRateDto;
import com.toyota.mainapp.dto.ValidationError;

import java.util.List;

/**
 * Interface for validation rules that can be applied to rate data
 */
public interface ValidationRule {

    /**
     * Validate a normalized rate according to this rule
     * 
     * @param rate The rate to validate
     * @return List of validation errors (empty list if validation passed)
     */
    List<ValidationError> validate(NormalizedRateDto rate);
}
