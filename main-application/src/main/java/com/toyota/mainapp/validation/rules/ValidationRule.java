package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.ValidationError;

import java.util.List;

/**
 * Interface for validation rules that check rate data
 */
public interface ValidationRule {
    
    /**
     * Validates a rate DTO and returns any validation errors
     * 
     * @param rate The rate to validate
     * @return List of validation errors (empty if validation passes)
     */
    List<ValidationError> validate(BaseRateDto rate);
}
