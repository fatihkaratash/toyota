package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.ValidationError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Toyota Financial Data Platform - Price Validation Rule
 * 
 * Validates bid and ask price values ensuring they are positive, non-null,
 * and above configurable minimum thresholds. Part of the comprehensive
 * rate validation pipeline for financial data integrity.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Component
public class PriceRule implements ValidationRule {


    private final BigDecimal minValue;
    public PriceRule(@Value("${validation.price.min-value:0.000001}") BigDecimal minValue) {
        this.minValue = minValue;
    }

    @Override
    public List<ValidationError> validate(BaseRateDto rate) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Check bid is positive and above minimum
        if (rate.getBid() == null) {
            errors.add(new ValidationError(
                "bid",
                null,
                "Bid price cannot be null"
            ));
        } else if (rate.getBid().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ValidationError(
                "bid",
                rate.getBid(),
                "Bid price must be positive"
            ));
        } else if (rate.getBid().compareTo(minValue) < 0) {
            errors.add(new ValidationError(
                "bid",
                rate.getBid(),
                "Bid price is below minimum allowed value of " + minValue
            ));
        }
        
        // Check ask is positive and above minimum
        if (rate.getAsk() == null) {
            errors.add(new ValidationError(
                "ask",
                null,
                "Ask price cannot be null"
            ));
        } else if (rate.getAsk().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ValidationError(
                "ask",
                rate.getAsk(),
                "Ask price must be positive"
            ));
        } else if (rate.getAsk().compareTo(minValue) < 0) {
            errors.add(new ValidationError(
                "ask",
                rate.getAsk(),
                "Ask price is below minimum allowed value of " + minValue
            ));
        }
        
        return errors;
    }
}
