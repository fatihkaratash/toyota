package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.ValidationError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates that the spread between bid and ask is within acceptable limits
 */
@Component
public class SpreadRule implements ValidationRule {


    private final BigDecimal maxSpreadPercentage;
    public SpreadRule(@Value("${validation.spread.max-percentage:5.0}") BigDecimal maxSpreadPercentage) {
        this.maxSpreadPercentage = maxSpreadPercentage;
    }

    @Override
    public List<ValidationError> validate(BaseRateDto rate) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Skip validation if bid or ask is null
        if (rate.getBid() == null || rate.getAsk() == null) {
            return errors;
        }
        
        try {
            // Calculate spread percentage
            BigDecimal spread = rate.getAsk().subtract(rate.getBid());
            
            // Use bid price as the base for percentage calculation
            BigDecimal spreadPercentage = spread.multiply(BigDecimal.valueOf(100))
                    .divide(rate.getBid(), 4, java.math.RoundingMode.HALF_UP);
            
            // Check if spread exceeds limit
            if (spreadPercentage.compareTo(maxSpreadPercentage) > 0) {
                errors.add(new ValidationError(
                    "spread",
                    spreadPercentage.toString(),
                    String.format("Spread percentage %s%% exceeds max allowed %s%%", 
                            spreadPercentage.toString(), maxSpreadPercentage.toString())
                ));
            }
        } catch (ArithmeticException e) {
            errors.add(new ValidationError(
                "spread-calculation",
                String.format("bid=%s, ask=%s", rate.getBid(), rate.getAsk()),
                "Error calculating spread: " + e.getMessage()
            ));
        }
        
        return errors;
    }
}
