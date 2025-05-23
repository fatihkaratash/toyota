package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.ValidationError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates that the timestamp is within acceptable range
 */
@Component
public class TimestampRule implements ValidationRule {

    /**
     * Maximum allowed age of rate data (milliseconds)
     */
    private final long maxAgeMs;
    
    /**
     * Constructor with configuration from application.properties
     */
    public TimestampRule(@Value("${validation.timestamp.max-age-seconds:300}") long maxAgeSeconds) {
        this.maxAgeMs = maxAgeSeconds * 1000;
    }

    @Override
    public List<ValidationError> validate(BaseRateDto rate) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Check timestamp is present
        if (rate.getTimestamp() == null) {
            errors.add(new ValidationError(
                "timestamp",
                "null",
                "Timestamp is required"
            ));
            return errors;
        }
        
        long currentTime = System.currentTimeMillis();
        long rateTime = rate.getTimestamp();
        
        // Check if timestamp is in the future
        if (rateTime > currentTime + 10000) { // Allow 10 seconds clock skew
            errors.add(new ValidationError(
                "timestamp",
                String.valueOf(rateTime),
                String.format("Timestamp is in the future: %s (current: %s, diff: %d ms)", 
                        Instant.ofEpochMilli(rateTime), Instant.ofEpochMilli(currentTime), rateTime - currentTime)
            ));
        }
        
        // Check if rate is too old
        if (currentTime - rateTime > maxAgeMs) {
            errors.add(new ValidationError(
                "timestamp",
                String.valueOf(rateTime),
                String.format("Rate is too old: %s, age: %d ms, max allowed: %d ms", 
                        Instant.ofEpochMilli(rateTime), currentTime - rateTime, maxAgeMs)
            ));
        }
        
        return errors;
    }
}
