package com.toyota.mainapp.util;

import com.toyota.mainapp.dto.model.BaseRateDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for validating rate data
 */
public final class RateValidationUtils {

    private RateValidationUtils() {
        // Prevent instantiation
    }
    
    public static List<String> validateBasicFields(BaseRateDto rate) {
        if (rate == null) {
            return Collections.singletonList("Rate object is null");
        }
        
        List<String> errors = new ArrayList<>();

        if (rate.getSymbol() == null || rate.getSymbol().trim().isEmpty()) {
            errors.add("Symbol is required");
        }
        
        if (rate.getProviderName() == null || rate.getProviderName().trim().isEmpty()) {
            errors.add("Provider name is required");
        }
        
        return errors;
    }

    public static List<String> validatePrices(BaseRateDto rate) {
        if (rate == null) {
            return Collections.singletonList("Rate object is null");
        }
        
        List<String> errors = new ArrayList<>();
        
        // Check for null values
        if (rate.getBid() == null) {
            errors.add("Bid price is null");
            return errors; 
        }
        
        if (rate.getAsk() == null) {
            errors.add("Ask price is null");
            return errors; 
        }
        
        // Check for positive values
        if (rate.getBid().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Bid price must be positive");
        }
        
        if (rate.getAsk().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Ask price must be positive");
        }
        
        // Check bid < ask
        if (rate.getBid().compareTo(rate.getAsk()) > 0) {
            errors.add("Bid price cannot be greater than ask price");
        }
        
        return errors;
    }

    public static boolean hasAllRequiredRates(Map<String, BaseRateDto> rates, List<String> requiredSymbols) {
        if (rates == null || requiredSymbols == null) {
            return false;
        }
        
        for (String symbol : requiredSymbols) {
            BaseRateDto rate = rates.get(symbol);
            
            // Check variants if symbol not found directly
            if (rate == null) {
                boolean found = false;
                for (String variant : SymbolUtils.generateSymbolVariants(symbol)) {
                    if (rates.containsKey(variant)) {
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    return false;
                }
            }
            
            // Validate the rate if found
            if (rate != null && (rate.getBid() == null || rate.getAsk() == null)) {
                return false;
            }
        }
        
        return true;
    }
}
