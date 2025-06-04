package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.dto.model.InputRateInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.toyota.mainapp.util.RateCalculationUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;


/**
 * Calculation strategy that averages rates from multiple providers
 */
@Component("averageCalculationStrategy")
@Slf4j
public class AverageCalculationStrategy implements CalculationStrategy {

    @Override
    public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        if (inputRates == null || inputRates.isEmpty()) {
            log.warn("No input rates provided for calculation rule: {}", rule.getOutputSymbol());
            return Optional.empty();
        }
        
        try {
            // Use the utility for average calculation
            Optional<RateCalculationUtils.AverageResult> avgResult = 
                    RateCalculationUtils.calculateAverage(inputRates);
                    
            if (avgResult.isEmpty()) {
                log.warn("Could not calculate average for {}: No valid inputs available", rule.getOutputSymbol());
                return Optional.empty();
            }
            
            // Create the rate DTO from the result
            BaseRateDto calculatedRate = RateCalculationUtils.createAverageRate(
                    rule.getOutputSymbol(), 
                    avgResult.get(), 
                    "AVERAGE");
                    
            log.info("Successfully calculated average rate for {}: bid={}, ask={}, using {} inputs", 
                    calculatedRate.getSymbol(), calculatedRate.getBid(), calculatedRate.getAsk(), 
                    avgResult.get().validRateCount());
                    
            return Optional.of(calculatedRate);
        } catch (Exception e) {
            log.error("Error calculating average for {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public String getStrategyName() {
        return "averageCalculationStrategy";
    }
}
