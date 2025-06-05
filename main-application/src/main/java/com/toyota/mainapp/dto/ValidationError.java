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
    
    private String field;
    private Object value;
    private String message;
}
