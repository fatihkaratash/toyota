package com.toyota.mainapp.validation;

import com.toyota.mainapp.dto.model.BaseRateDto;
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
    
    public void validate(BaseRateDto rate) throws AggregatedRateValidationException {

        if (rate == null) {
            throw new AggregatedRateValidationException(List.of("Rate object is null"));
        }
        
        List<String> allPreliminaryErrors = new ArrayList<>(validateBasicFields(rate));
        
        // Special handling for cross rates (they may have special format)
        boolean isCrossRate = isCrossRate(rate.getSymbol());
        
        // Handle missing bid/ask values
        if (rate.getBid() == null) {
            allPreliminaryErrors.add("Bid price is null");
        }
        if (rate.getAsk() == null) {
            allPreliminaryErrors.add("Ask price is null");
        }

        if (rate.getBid() != null && rate.getAsk() != null) {
            // Check simple price validation
            if (rate.getBid().compareTo(BigDecimal.ZERO) <= 0) {
                allPreliminaryErrors.add("Bid price must be positive");
            }
            
            if (rate.getAsk().compareTo(BigDecimal.ZERO) <= 0) {
                allPreliminaryErrors.add("Ask price must be positive");
            }
            
            if (rate.getBid().compareTo(rate.getAsk()) > 0) {
                String errorMsg = "Bid price cannot be greater than ask price";
                // Allow small tolerance for cross rates due to potential calculation imprecision
                if (isCrossRate && isWithinTolerance(rate.getBid(), rate.getAsk(), 0.001)) {
                    log.warn("Cross rate {} has bid ({}) slightly greater than ask ({}), but within tolerance",
                            rate.getSymbol(), rate.getBid(), rate.getAsk());
                } else {
                    allPreliminaryErrors.add(errorMsg);
                }
            }
        }
        
        if (!allPreliminaryErrors.isEmpty()) {
            throw new AggregatedRateValidationException(allPreliminaryErrors);
        }
        
        // Apply all complex validation rules
        List<ValidationError> complexErrors = new ArrayList<>();
        for (ValidationRule rule : validationRules) {
            try {
                List<ValidationError> ruleErrors = rule.validate(rate);
                if (ruleErrors != null && !ruleErrors.isEmpty()) {
                    complexErrors.addAll(ruleErrors);
                }
            } catch (Exception e) {
                log.error("Error executing validation rule {}: {}", 
                        rule.getClass().getSimpleName(), e.getMessage(), e);
                complexErrors.add(new ValidationError(
                    "rule-execution",
                    rule.getClass().getSimpleName(),
                    "Validation rule execution error: " + e.getMessage()
                ));
            }
        }
        
        // If there are complex errors, throw an exception
        if (!complexErrors.isEmpty()) {
            List<String> errorMessages = complexErrors.stream()
                .map(e -> e.getField() + ": " + e.getMessage())
                .collect(Collectors.toList());
                
            log.warn("Validation failed for {}: {}", 
                    rate.getSymbol(), String.join(", ", errorMessages));
                    
            throw new AggregatedRateValidationException(errorMessages);
        }
    }
    
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
    
    private boolean isWithinTolerance(BigDecimal val1, BigDecimal val2, double tolerancePercent) {
        BigDecimal diff = val1.subtract(val2).abs();
        BigDecimal tolerance = val2.multiply(BigDecimal.valueOf(tolerancePercent));
        return diff.compareTo(tolerance) <= 0;
    }

    private boolean isCrossRate(String symbol) {
        if (symbol == null) return false;
        
        String upperSymbol = symbol.toUpperCase();
        return upperSymbol.contains("EUR/TRY") || 
               upperSymbol.contains("EURTRY") ||
               upperSymbol.contains("GBP/TRY") || 
               upperSymbol.contains("GBPTRY");
    }
}
