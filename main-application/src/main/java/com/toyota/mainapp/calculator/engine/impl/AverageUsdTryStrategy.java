package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.util.RateCalculationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Specialized strategy for calculating average USD/TRY rate
 */
@Component("averageUsdTryStrategy")
@Slf4j
public class AverageUsdTryStrategy implements CalculationStrategy {

    @Override
    public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        log.info("Calculating average USD/TRY rate...");
        
        if (inputRates == null || inputRates.isEmpty()) {
            log.warn("No input rates available for calculation");
            return Optional.empty();
        }
        
        try {
            // Use the utility for average calculation
            Optional<RateCalculationUtils.AverageResult> avgResult = 
                    RateCalculationUtils.calculateAverage(inputRates);
                    
            if (avgResult.isEmpty()) {
                log.warn("No valid rates found for USD/TRY calculation");
                return Optional.empty();
            }
            
            // Create the rate DTO from the result
            BaseRateDto calculatedRate = RateCalculationUtils.createAverageRate(
                    rule.getOutputSymbol(), 
                    avgResult.get(), 
                    "averageUsdTryStrategy");
                    
            log.info("Successfully calculated USD/TRY average: {} using {} rates, bid={}, ask={}",
                    rule.getOutputSymbol(), avgResult.get().validRateCount(), 
                    calculatedRate.getBid(), calculatedRate.getAsk());
                    
            return Optional.of(calculatedRate);
        } catch (Exception e) {
            log.error("Error calculating USD/TRY average: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public String getStrategyName() {
        return "averageUsdTryStrategy";
    }
}
