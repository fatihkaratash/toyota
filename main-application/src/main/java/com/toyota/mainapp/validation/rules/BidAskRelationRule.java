package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.ValidationError;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Toyota Financial Data Platform - Bid-Ask Relationship Validation Rule
 * 
 * Ensures financial market integrity by validating that bid prices never exceed
 * ask prices. Critical component of the rate validation system preventing
 * invalid market data from propagating through the platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Component
public class BidAskRelationRule implements ValidationRule {

    @Override
    public List<ValidationError> validate(BaseRateDto rate) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Check that bid is less than or equal to ask
        if (rate.getBid() != null && rate.getAsk() != null) {
            if (rate.getBid().compareTo(rate.getAsk()) > 0) {
                errors.add(new ValidationError(
                    "bid-ask-relation",
                    String.format("bid=%s, ask=%s", rate.getBid(), rate.getAsk()),
                    "Bid price cannot be greater than ask price"
                ));
            }
        }
        
        return errors;
    }
}
