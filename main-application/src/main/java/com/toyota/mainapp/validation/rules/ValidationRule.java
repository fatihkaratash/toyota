package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.ValidationError;

import java.util.List;

/**
 * Interface for validation rules that check rate data
 */
public interface ValidationRule {
    
    List<ValidationError> validate(BaseRateDto rate);
}
