package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import com.toyota.mainapp.dto.RateType;
import com.toyota.mainapp.dto.common.InputRateInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
            // Create calculated rate DTO
            BaseRateDto calculatedRate = BaseRateDto.builder()
                .rateType(RateType.CALCULATED)
                .symbol(rule.getOutputSymbol())
                .timestamp(System.currentTimeMillis())
                .build();
            
            // Simple average calculation for bid and ask 
            BigDecimal totalBid = BigDecimal.ZERO;
            BigDecimal totalAsk = BigDecimal.ZERO;
            List<InputRateInfo> inputs = new ArrayList<>();
            
            // Process each input rate
            for (Map.Entry<String, BaseRateDto> entry : inputRates.entrySet()) {
                BaseRateDto rate = entry.getValue();
                
                // Validate rates before using in calculation
                if (rate.getBid() != null && rate.getAsk() != null) {
                    totalBid = totalBid.add(rate.getBid());
                    totalAsk = totalAsk.add(rate.getAsk());
                    
                    // Track input sources
                    inputs.add(new InputRateInfo(
                        rate.getSymbol(),
                        rate.getRateType() != null ? rate.getRateType().name() : "RAW",
                        rate.getProviderName(),
                        rate.getBid(),
                        rate.getAsk(),
                        rate.getTimestamp()
                    ));
                } else {
                    log.warn("Skipping rate with null bid/ask in calculation. Provider: {}, Symbol: {}",
                            rate.getProviderName(), rate.getSymbol());
                }
            }
            
            // Calculate average if we have data
            int validRateCount = inputs.size();
            if (validRateCount > 0) {
                calculatedRate.setBid(totalBid.divide(BigDecimal.valueOf(validRateCount), 6, RoundingMode.HALF_UP));
                calculatedRate.setAsk(totalAsk.divide(BigDecimal.valueOf(validRateCount), 6, RoundingMode.HALF_UP));
                calculatedRate.setCalculationInputs(inputs);
                calculatedRate.setCalculatedByStrategy("AVERAGE");
                
                log.info("Successfully calculated average rate for {}: bid={}, ask={}, using {} inputs", 
                        calculatedRate.getSymbol(), calculatedRate.getBid(), calculatedRate.getAsk(), validRateCount);
                        
                return Optional.of(calculatedRate);
            } else {
                log.warn("Could not calculate average for {}: No valid inputs available", rule.getOutputSymbol());
                return Optional.empty();
            }
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
