package com.toyota.mainapp.validation;

import com.toyota.mainapp.dto.NormalizedRateDto;
import com.toyota.mainapp.dto.ValidationError;
import com.toyota.mainapp.exception.AggregatedRateValidationException;
import com.toyota.mainapp.validation.rules.ValidationRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for validating rate data against multiple rules
 */
@Service
@Slf4j
public class RateValidatorService {

    private final List<ValidationRule> validationRules;

    /**
     * Constructor that auto-injects all available ValidationRule beans
     */
    public RateValidatorService(List<ValidationRule> validationRules) {
        this.validationRules = validationRules;
        log.info("Rate validator initialized with {} validation rules", validationRules.size());
    }

    /**
     * Validate a normalized rate against all rules
     * 
     * @param normalizedRateDto The rate to validate
     * @return The same rate if validation passes
     * @throws AggregatedRateValidationException if validation fails
     */
    public NormalizedRateDto validate(NormalizedRateDto normalizedRateDto) throws AggregatedRateValidationException {
        log.debug("Validating rate: {}", normalizedRateDto);
        
        List<ValidationError> allErrors = new ArrayList<>();
        
        // Apply each validation rule and collect errors
        for (ValidationRule rule : validationRules) {
            List<ValidationError> errors = rule.validate(normalizedRateDto);
            if (errors != null && !errors.isEmpty()) {
                allErrors.addAll(errors);
            }
        }
        
        // If any errors were found, throw exception
        if (!allErrors.isEmpty()) {
            log.warn("Rate validation failed for symbol {}: {}", normalizedRateDto.getSymbol(), allErrors);
            throw new AggregatedRateValidationException(allErrors);
        }
        
        log.debug("Rate validation successful for symbol {}", normalizedRateDto.getSymbol());
        return normalizedRateDto;
    }
}
