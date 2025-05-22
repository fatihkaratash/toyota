package com.toyota.mainapp.exception;

import com.toyota.mainapp.dto.ValidationError;

import java.util.Collections;
import java.util.List;

/**
 * Exception that contains multiple validation errors
 * from the rate validation process
 */
public class AggregatedRateValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public AggregatedRateValidationException(List<ValidationError> errors) {
        super("Rate validation failed with " + errors.size() + " error(s)");
        this.errors = errors;
    }

    public AggregatedRateValidationException(String message, List<ValidationError> errors) {
        super(message);
        this.errors = errors;
    }

    /**
     * Get all validation errors
     * @return Unmodifiable list of validation errors
     */
    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
