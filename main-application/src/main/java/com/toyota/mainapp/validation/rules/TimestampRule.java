package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.NormalizedRateDto;
import com.toyota.mainapp.dto.ValidationError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates that the timestamp on a rate is within acceptable bounds
 */
@Component
public class TimestampRule implements ValidationRule {

    /**
     * Maximum age of a rate in seconds
     */
    private final int maxAgeSeconds;

    /**
     * Constructor with configuration from application.properties
     */
    public TimestampRule(@Value("${validation.timestamp.max-age-seconds:60}") int maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }

    @Override
    public List<ValidationError> validate(NormalizedRateDto rate) {
        List<ValidationError> errors = new ArrayList<>();
        long currentTimeMs = Instant.now().toEpochMilli();
        long maxAgeMs = maxAgeSeconds * 1000L;
        
        // Check if timestamp is too old
        if (currentTimeMs - rate.getTimestamp() > maxAgeMs) {
            errors.add(new ValidationError(
                "timestamp",
                rate.getTimestamp(),
                "Rate timestamp is too old. Maximum age is " + maxAgeSeconds + " seconds"
            ));
        }
        
        // Check if timestamp is in the future
        if (rate.getTimestamp() > currentTimeMs + 5000) { // 5 seconds tolerance for clock skew
            errors.add(new ValidationError(
                "timestamp", 
                rate.getTimestamp(),
                "Rate timestamp is in the future"
            ));
        }
        
        return errors;
    }
}
