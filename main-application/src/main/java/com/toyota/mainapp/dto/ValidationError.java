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
     * Field name with the validation error
     */
    private String field;
    
    /**
     * Invalid value
     */
    private Object value;
    
    /**
     * Error message
     */
    private String message;
}
