package com.toyota.mainapp.calculator;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import com.toyota.mainapp.dto.RateType;
import com.toyota.mainapp.dto.common.InputRateInfo;
import com.toyota.mainapp.kafka.publisher.SequentialPublisher;
import com.toyota.mainapp.mapper.RateMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating derived rates from raw rates
 */
@Service
@Slf4j
public class RateCalculatorService {

    private final RateCacheService rateCacheService;
    private final RuleEngineService ruleEngineService;
    private final RateMapper rateMapper;
    
    // Break circular dependency with setter injection
    private SequentialPublisher sequentialPublisher;

    public RateCalculatorService(RateCacheService rateCacheService,
                                RuleEngineService ruleEngineService,
                                RateMapper rateMapper) {
        this.rateCacheService = rateCacheService;
        this.ruleEngineService = ruleEngineService;
        this.rateMapper = rateMapper;
        log.info("RateCalculatorService initialized");
    }
    
    @Autowired
    public void setSequentialPublisher(SequentialPublisher sequentialPublisher) {
        this.sequentialPublisher = sequentialPublisher;
        log.info("SequentialPublisher set in RateCalculatorService");
    }
    
    /**
     * Calculate derived rates from aggregated data collected by the window aggregator
     */
    @CircuitBreaker(name = "calculatorService")
    @Retry(name = "calculatorRetry")
    public void calculateFromAggregatedData(String baseSymbol, Map<String, BaseRateDto> aggregatedRates) {
        log.info("Calculating rates from aggregated data for symbol: {}, providers: {}", 
                baseSymbol, String.join(", ", aggregatedRates.keySet()));
        
        try {
            // Find calculation rules for this base symbol
            List<CalculationRuleDto> rules = ruleEngineService.getRulesByInputBaseSymbol(baseSymbol);
            
            if (rules.isEmpty()) {
                log.info("No calculation rules found for base symbol: {}", baseSymbol);
                return;
            }
            
            log.debug("Found {} calculation rules for base symbol: {}", rules.size(), baseSymbol);
            
            // Process each rule
            for (CalculationRuleDto rule : rules) {
                calculateRateFromRule(rule, aggregatedRates);
            }
            
        } catch (Exception e) {
            log.error("Error calculating rates from aggregated data: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Calculate a single rate using the provided rule and aggregated data
     */
    private void calculateRateFromRule(CalculationRuleDto rule, Map<String, BaseRateDto> aggregatedRates) {
        try {
            log.debug("Executing calculation rule: {}", rule.getOutputSymbol());
            
            if (aggregatedRates.isEmpty()) {
                log.warn("No aggregated rates available for calculation: {}", rule.getOutputSymbol());
                return;
            }
            
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
            
            // Process each provider's data
            for (Map.Entry<String, BaseRateDto> entry : aggregatedRates.entrySet()) {
                BaseRateDto rate = entry.getValue();
                
                // Validate rates before using in calculation
                if (rate.getBid() != null && rate.getAsk() != null) {
                    totalBid = totalBid.add(rate.getBid());
                    totalAsk = totalAsk.add(rate.getAsk());
                    
                    // Track input sources
                    inputs.add(new InputRateInfo(
                        rate.getSymbol(),
                        rate.getRateType().name(),
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
                
                // Cache the calculated rate
                rateCacheService.cacheCalculatedRate(calculatedRate);
                
                // Publish to Kafka
                sequentialPublisher.publishRate(calculatedRate);
                
                log.info("Successfully calculated and published rate: {} using {} input rates", 
                      calculatedRate.getSymbol(), validRateCount);
            } else {
                log.warn("Could not calculate rate: {}. No valid inputs available.", rule.getOutputSymbol());
            }
            
        } catch (Exception e) {
            log.error("Error calculating rate for rule {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
        }
    }
}
