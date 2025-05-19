package com.toyota.mainapp.validation.rule.impl;

import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.validation.ValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Validation rule that ensures the timestamp of a rate is reasonably current
 * and not too far in the future.
 */
@Component
public class TimestampRule implements ValidationRule {
    
    private static final Logger logger = LoggerFactory.getLogger(TimestampRule.class);
    
    @Value("${app.validation.max-timestamp-age-seconds:300}")
    private long maxAgeSeconds;
    
    @Value("${app.validation.max-timestamp-future-seconds:10}")
    private long maxFutureSeconds;
    
    @Override
    public boolean validate(Rate rate) {
        if (rate == null || rate.getFields() == null || rate.getFields().getTimestamp() == null) {
            logger.warn("Kur, kur alanları veya zaman damgası null, zaman damgası doğrulanamıyor");
            return false;
        }
        
        Instant now = Instant.now();
        Instant timestamp = rate.getFields().getTimestamp();
        
        // Check if timestamp is too old
        Duration age = Duration.between(timestamp, now);
        if (timestamp.isAfter(now) && age.abs().getSeconds() > maxFutureSeconds) { // Timestamp is in the future
             logger.warn("{} kuru için zaman damgası çok ileride: {} saniye",
                       rate.getSymbol(), age.abs().getSeconds());
            return false;
        } else if (timestamp.isBefore(now) && age.getSeconds() > maxAgeSeconds) { // Timestamp is in the past
            logger.warn("{} kuru için zaman damgası çok eski: {} saniye", 
                       rate.getSymbol(), age.getSeconds());
            return false;
        }
        
        logger.debug("{} kuru zaman damgası doğrulamasını geçti", rate.getSymbol());
        return true;
    }
    
    @Override
    public String getName() {
        return "TimestampRule";
    }
    
    @Override
    public String getDescription() {
        return "Zaman damgasının makul ölçüde güncel olduğunu ve çok ileride olmadığını doğrular";
    }
}
