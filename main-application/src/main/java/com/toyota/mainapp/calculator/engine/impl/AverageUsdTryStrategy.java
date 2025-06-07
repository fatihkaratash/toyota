package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.util.RateCalculationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component("averageUsdTryStrategy")
@Slf4j
public class AverageUsdTryStrategy implements CalculationStrategy {
    
    @Override
    public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        log.debug("Calculating average for USDTRY with {} input rates", inputRates.size());
        
        Optional<RateCalculationUtils.AverageResult> avgResult = 
            RateCalculationUtils.calculateAverage(inputRates);
            
        if (avgResult.isEmpty()) {
            log.warn("No valid rates found for average calculation: {}", rule.getOutputSymbol());
            return Optional.empty();
        }
        
        BaseRateDto result = RateCalculationUtils.createAverageRate(
            rule.getOutputSymbol(), avgResult.get(), "AVERAGE");
            
        log.debug("Successfully calculated USDTRY average: bid={}, ask={}", 
                result.getBid(), result.getAsk());
                
        return Optional.of(result);
    }
    
    @Override
    public String getStrategyName() {
        return "averageUsdTryStrategy";
    }
}