package com.toyota.mainapp.exception;

import java.util.Collections;
import java.util.List;

/**
 * Exception that contains multiple validation errors
 * from the rate validation process
 */
public class AggregatedRateValidationException extends RuntimeException {

    private final List<String> errors;

    public AggregatedRateValidationException(List<String> errors) {
        super("Rate validation failed with " + errors.size() + " error(s): " + String.join("; ", errors));
        this.errors = errors;
    }

    public AggregatedRateValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    /**
     * Get all validation errors
     * @return Unmodifiable list of validation errors
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
