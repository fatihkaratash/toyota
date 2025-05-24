package com.toyota.mainapp.validation.rules;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.ValidationError;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates that the bid-ask relationship is valid (bid <= ask)
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
