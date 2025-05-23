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
            BigDecimal totalBid = BigDecimal.ZERO;
            BigDecimal totalAsk = BigDecimal.ZERO;
            List<InputRateInfo> inputs = new ArrayList<>();
            int validRatesCount = 0;
            long latestTimestamp = 0;
            
            // Process all input rates
            for (Map.Entry<String, BaseRateDto> entry : inputRates.entrySet()) {
                BaseRateDto rate = entry.getValue();
                
                if (rate.getBid() != null && rate.getAsk() != null) {
                    totalBid = totalBid.add(rate.getBid());
                    totalAsk = totalAsk.add(rate.getAsk());
                    validRatesCount++;
                    
                    // Track the latest timestamp
                    if (rate.getTimestamp() != null && rate.getTimestamp() > latestTimestamp) {
                        latestTimestamp = rate.getTimestamp();
                    }
                    
                    // Record input sources
                    inputs.add(new InputRateInfo(
                        rate.getSymbol(),
                        rate.getRateType() != null ? rate.getRateType().name() : "RAW",
                        rate.getProviderName(),
                        rate.getBid(),
                        rate.getAsk(),
                        rate.getTimestamp()
                    ));
                    
                    log.debug("Added rate to average calculation: {} from {}, bid={}, ask={}",
                              rate.getSymbol(), rate.getProviderName(), rate.getBid(), rate.getAsk());
                }
            }
            
            if (validRatesCount == 0) {
                log.warn("No valid rates found for calculation");
                return Optional.empty();
            }
            
            // Calculate average
            BigDecimal averageBid = totalBid.divide(BigDecimal.valueOf(validRatesCount), 6, RoundingMode.HALF_UP);
            BigDecimal averageAsk = totalAsk.divide(BigDecimal.valueOf(validRatesCount), 6, RoundingMode.HALF_UP);
            
            // Use latest timestamp, or current time if none is available
            long timestamp = latestTimestamp > 0 ? latestTimestamp : System.currentTimeMillis();
            
            // Create result
            BaseRateDto resultRate = BaseRateDto.builder()
                .rateType(RateType.CALCULATED)
                .symbol(rule.getOutputSymbol())
                .bid(averageBid)
                .ask(averageAsk)
                .timestamp(timestamp)
                .calculationInputs(inputs)
                .calculatedByStrategy("averageUsdTryStrategy")
                .build();
                
            log.info("Successfully calculated USD/TRY average: {} using {} rates, bid={}, ask={}",
                      rule.getOutputSymbol(), validRatesCount, averageBid, averageAsk);
                      
            return Optional.of(resultRate);
            
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
