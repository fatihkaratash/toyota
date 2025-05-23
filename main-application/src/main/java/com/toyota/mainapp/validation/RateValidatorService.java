package com.toyota.mainapp.validation;

import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.ValidationError;
import com.toyota.mainapp.exception.AggregatedRateValidationException;
import com.toyota.mainapp.validation.rules.ValidationRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for validating rate data using validation rules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateValidatorService {

    private final List<ValidationRule> validationRules;
    
    /**
     * Validate a rate using all registered validation rules
     *
     * @param rate The rate to validate
     * @throws AggregatedRateValidationException if validation fails
     */
    public void validate(BaseRateDto rate) throws AggregatedRateValidationException {
        // Basic null check
        if (rate == null) {
            throw new AggregatedRateValidationException(List.of("Rate object is null"));
        }
        
        // Pre-validation basic checks
        List<String> basicErrors = validateBasicFields(rate);
        if (!basicErrors.isEmpty()) {
            throw new AggregatedRateValidationException(basicErrors);
        }
        
        // Handle missing bid/ask values
        if (rate.getBid() == null || rate.getAsk() == null) {
            List<String> priceErrors = new ArrayList<>();
            if (rate.getBid() == null) priceErrors.add("Bid price is null");
            if (rate.getAsk() == null) priceErrors.add("Ask price is null");
            throw new AggregatedRateValidationException(priceErrors);
        }
        
        // Check simple price validation before passing to complex rules
        if (rate.getBid().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AggregatedRateValidationException(List.of("Bid price must be positive"));
        }
        
        if (rate.getAsk().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AggregatedRateValidationException(List.of("Ask price must be positive"));
        }
        
        if (rate.getBid().compareTo(rate.getAsk()) > 0) {
            throw new AggregatedRateValidationException(List.of("Bid price cannot be greater than ask price"));
        }
        
        // Apply all validation rules
        List<ValidationError> errors = new ArrayList<>();
        for (ValidationRule rule : validationRules) {
            try {
                List<ValidationError> ruleErrors = rule.validate(rate);
                if (ruleErrors != null && !ruleErrors.isEmpty()) {
                    errors.addAll(ruleErrors);
                }
            } catch (Exception e) {
                log.error("Error executing validation rule {}: {}", 
                        rule.getClass().getSimpleName(), e.getMessage(), e);
                errors.add(new ValidationError(
                    "rule-execution",
                    rule.getClass().getSimpleName(),
                    "Validation rule execution error: " + e.getMessage()
                ));
            }
        }
        
        // If there are errors, throw an exception
        if (!errors.isEmpty()) {
            List<String> errorMessages = errors.stream()
                .map(e -> e.getField() + ": " + e.getMessage())
                .collect(Collectors.toList());
                
            log.warn("Validation failed for {}: {}", 
                    rate.getSymbol(), String.join(", ", errorMessages));
                    
            throw new AggregatedRateValidationException(errorMessages);
        }
    }
    
    /**
     * Validate basic fields of a rate
     */
    private List<String> validateBasicFields(BaseRateDto rate) {
        List<String> errors = new ArrayList<>();
        
        // Check required fields
        if (rate.getSymbol() == null || rate.getSymbol().trim().isEmpty()) {
            errors.add("Symbol is required");
        }
        
        if (rate.getProviderName() == null || rate.getProviderName().trim().isEmpty()) {
            errors.add("Provider name is required");
        }
        
        return errors;
    }
}
