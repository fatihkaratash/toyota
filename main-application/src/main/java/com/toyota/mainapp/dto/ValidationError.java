package com.toyota.mainapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single validation error that occurred during rate validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {
    
    /**
     * Name of the field that failed validation (optional)
     */
    private String fieldName;
    
    /**
     * The value that was rejected (optional)
     */
    private Object rejectedValue;
    
    /**
     * A human-readable error message describing the validation failure
     */
    private String message;
    
    /**
     * Create a validation error with just a message
     */
    public ValidationError(String message) {
        this.message = message;
    }
    
    /**
     * Create a validation error with field name and message
     */
    public ValidationError(String fieldName, String message) {
        this.fieldName = fieldName;
        this.message = message;
    }
}
