package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.NormalizedRateDto;
import com.toyota.mainapp.dto.ValidationError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates that the spread (ask-bid) is reasonable
 */
@Component
public class SpreadRule implements ValidationRule {

    /**
     * Maximum allowed spread as a percentage (e.g., 0.05 for 5%)
     */
    private final double maxSpreadPercentage;

    /**
     * Constructor with configuration from application.properties
     */
    public SpreadRule(@Value("${validation.spread.max-percentage:0.05}") double maxSpreadPercentage) {
        this.maxSpreadPercentage = maxSpreadPercentage;
    }

    @Override
    public List<ValidationError> validate(NormalizedRateDto rate) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Check if bid <= ask (basic check)
        if (rate.getBid().compareTo(rate.getAsk()) > 0) {
            errors.add(new ValidationError(
                "spread",
                "bid=" + rate.getBid() + ", ask=" + rate.getAsk(),
                "Bid price must be less than or equal to ask price"
            ));
            return errors; // No need for further checks
        }
        
        // Check if spread is within acceptable percentage
        if (rate.getBid().compareTo(BigDecimal.ZERO) > 0) { // Avoid division by zero
            // Calculate spread percentage: (ask - bid) / bid
            BigDecimal spread = rate.getAsk().subtract(rate.getBid());
            BigDecimal spreadPercentage = spread.divide(rate.getBid(), 6, BigDecimal.ROUND_HALF_UP);
            
            if (spreadPercentage.doubleValue() > maxSpreadPercentage) {
                errors.add(new ValidationError(
                    "spread",
                    spreadPercentage,
                    "Spread percentage " + spreadPercentage + " exceeds maximum allowed " + maxSpreadPercentage
                ));
            }
        }
        
        return errors;
    }
}
