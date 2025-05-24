package com.toyota.mainapp.calculator;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.dependency.RateDependencyManager;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.dto.model.InputRateInfo;
import com.toyota.mainapp.kafka.KafkaPublishingService;
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
    private final RateDependencyManager rateDependencyManager; 
    
    // Break circular dependency with setter injection
    private KafkaPublishingService sequentialPublisher;

    public RateCalculatorService(RateCacheService rateCacheService,
                                RuleEngineService ruleEngineService,
                                RateMapper rateMapper,
                                RateDependencyManager rateDependencyManager) { 
        this.rateCacheService = rateCacheService;
        this.ruleEngineService = ruleEngineService;
        this.rateMapper = rateMapper;
        this.rateDependencyManager = rateDependencyManager; 
        log.info("RateCalculatorService initialized");
    }
    
    @Autowired
    public void setSequentialPublisher(KafkaPublishingService sequentialPublisher) {
        this.sequentialPublisher = sequentialPublisher;
        log.info("SequentialPublisher set in RateCalculatorService");
    }
    
    /**
     * Processes a window of raw rates that have just been completed by the aggregator.
     * This method will find rules triggered by these raw rates and attempt to execute them.
     * @param newlyCompletedRawRatesInWindow A map of providerSpecificSymbol to BaseRateDto.
     */
    @CircuitBreaker(name = "calculatorService")
    @Retry(name = "calculatorRetry")
    public void processWindowCompletion(Map<String, BaseRateDto> newlyCompletedRawRatesInWindow) {
        if (newlyCompletedRawRatesInWindow == null || newlyCompletedRawRatesInWindow.isEmpty()) {
            log.debug("processWindowCompletion called with no new raw rates.");
            return;
        }
        log.info("Processing window completion with {} raw rates: {}", 
                newlyCompletedRawRatesInWindow.size(), newlyCompletedRawRatesInWindow.keySet());

        Set<CalculationRuleDto> rulesToProcess = new HashSet<>();
        for (String providerSpecificSymbol : newlyCompletedRawRatesInWindow.keySet()) {
            // For each raw rate that completed the window, find rules that depend on it.
            // These should be the _AVG rules.
            log.debug("Checking rules triggered by raw symbol: {}", providerSpecificSymbol);
            rulesToProcess.addAll(rateDependencyManager.getCalculationsToTrigger(providerSpecificSymbol, true));
        }
        
        if (rulesToProcess.isEmpty()) {
            log.info("No calculation rules triggered by the completed window for symbols: {}", newlyCompletedRawRatesInWindow.keySet());
            return;
        }

        log.info("Triggering {} rules based on window completion: {}", rulesToProcess.size(),
                rulesToProcess.stream().map(CalculationRuleDto::getOutputSymbol).collect(Collectors.joining(", ")));
        
        // Sort rules by priority before execution
        List<CalculationRuleDto> sortedRulesToProcess = rulesToProcess.stream()
            .sorted(Comparator.comparingInt(CalculationRuleDto::getPriority))
            .collect(Collectors.toList());

        for (CalculationRuleDto rule : sortedRulesToProcess) {
            // Use RuleEngineService to execute the rule
            try {
                // You need to provide the required aggregatedRates map as the second argument.
                // Replace 'newlyCompletedRawRatesInWindow' with the appropriate rates map if needed.
                ruleEngineService.executeRule(rule, newlyCompletedRawRatesInWindow);
            } catch (Exception e) {
                log.error("Error executing rule {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
            }
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
