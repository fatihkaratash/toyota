package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ✅ AVERAGE STRATEGY: Configuration-driven average calculation
 * Processes AVG type rules from calculation-config.json
 */
@Component("averageCalculationStrategy")
@Slf4j
public class AverageCalculationStrategy implements CalculationStrategy {

    @Override
    public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        log.debug("Calculating average for: {} with {} input rates", 
                rule.getOutputSymbol(), inputRates.size());

        if (inputRates.isEmpty()) {
            log.warn("No input rates available for average calculation: {}", rule.getOutputSymbol());
            return Optional.empty();
        }

        // ✅ FIXED: Get target symbol from rule configuration
        List<String> targetSymbols = rule.getInputSymbols();
        if (targetSymbols == null || targetSymbols.isEmpty()) {
            log.warn("No input symbols defined in rule: {}", rule.getOutputSymbol());
            return Optional.empty();
        }

        // ✅ FIXED: More flexible symbol matching for providers
        List<BaseRateDto> validRates = inputRates.values().stream()
                .filter(rate -> rate != null && rate.getBid() != null && rate.getAsk() != null)
                .filter(rate -> matchesTargetSymbol(rate, targetSymbols))
                .filter(rate -> isValidForAveraging(rate, rule))
                .toList();

        if (validRates.isEmpty()) {
            log.warn("No valid rates found for average calculation: {} from symbols: {}", 
                    rule.getOutputSymbol(), targetSymbols);
            return Optional.empty();
        }

        // ✅ CONFIG-DRIVEN: Check minimum provider requirement
        int minProviders = getMinProviders(rule);
        if (validRates.size() < minProviders) {
            log.warn("Insufficient providers for {}: found={}, required={}", 
                    rule.getOutputSymbol(), validRates.size(), minProviders);
            return Optional.empty();
        }

        // ✅ CALCULATE: Equal weighted average
        try {
            BigDecimal avgBid = calculateAverage(validRates.stream()
                    .map(BaseRateDto::getBid)
                    .toList());
                    
            BigDecimal avgAsk = calculateAverage(validRates.stream()
                    .map(BaseRateDto::getAsk)
                    .toList());

            // ✅ CREATE RESULT: Build average rate with config-driven symbol
            BaseRateDto result = BaseRateDto.builder()
                    .symbol(rule.getOutputSymbol())
                    .bid(avgBid)
                    .ask(avgAsk)
                    .rateType(RateType.CALCULATED)
                    .providerName("AverageCalculator")
                    .timestamp(System.currentTimeMillis())
                    .calculatedByStrategy(getStrategyName())
                    .build();

            log.info("✅ Average calculated: {} = {} bid, {} ask (from {} providers)", 
                    result.getSymbol(), result.getBid(), result.getAsk(), validRates.size());

            return Optional.of(result);

        } catch (Exception e) {
            log.error("❌ Average calculation failed for {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public String getStrategyName() {
        return "averageCalculationStrategy";
    }

    @Override
    public String getStrategyType() {
        return "AVG";
    }

    @Override
    public boolean canHandle(CalculationRuleDto rule) {
        return rule != null && 
               "AVG".equalsIgnoreCase(rule.getType()) &&
               "averageCalculationStrategy".equals(rule.getStrategyType());
    }

    /**
     * ✅ FIXED: Better symbol matching logic
     */
    private boolean matchesTargetSymbol(BaseRateDto rate, List<String> targetSymbols) {
        if (rate.getSymbol() == null) return false;
        
        String rateSymbol = rate.getSymbol().toUpperCase();
        String normalizedRateSymbol = rateSymbol.replaceAll("_AVG|_CROSS|_CALC", "");
        
        return targetSymbols.stream().anyMatch(target -> {
            String normalizedTarget = target.toUpperCase().replaceAll("_AVG|_CROSS|_CALC", "");
            return normalizedRateSymbol.equals(normalizedTarget) || 
                   rateSymbol.equals(normalizedTarget);
        });
    }

    /**
     * ✅ VALIDATION: Check if rate is valid for averaging
     */
    private boolean isValidForAveraging(BaseRateDto rate, CalculationRuleDto rule) {
        // Check rate age if configured
        long maxAge = getMaxAge(rule);
        if (maxAge > 0 && rate.getTimestamp() != null) {
            long age = System.currentTimeMillis() - rate.getTimestamp();
            if (age > maxAge) {
                log.debug("Rate too old for averaging: {} age={}ms, max={}ms", 
                        rate.getSymbol(), age, maxAge);
                return false;
            }
        }

        // Check bid/ask validity
        return rate.getBid().compareTo(BigDecimal.ZERO) > 0 && 
               rate.getAsk().compareTo(BigDecimal.ZERO) > 0 &&
               rate.getAsk().compareTo(rate.getBid()) >= 0;
    }

    /**
     * ✅ AVERAGE CALCULATION: Equal weighted average with proper rounding
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(values.size()), 5, RoundingMode.HALF_UP);
    }

    /**
     * ✅ FIXED: String parameter handling
     */
    private int getMinProviders(CalculationRuleDto rule) {
        if (rule.getInputParameters() != null && rule.getInputParameters().containsKey("minProviders")) {
            Object minProvidersObj = rule.getInputParameters().get("minProviders");
            try {
                if (minProvidersObj instanceof String) {
                    return Integer.parseInt((String) minProvidersObj);
                } else if (minProvidersObj instanceof Number) {
                    return ((Number) minProvidersObj).intValue();
                }
            } catch (Exception e) {
                log.warn("Invalid minProviders parameter for {}, using default 1", rule.getOutputSymbol());
            }
        }
        return 1; // Default minimum
    }

    /**
     * ✅ FIXED: String parameter handling
     */
    private long getMaxAge(CalculationRuleDto rule) {
        if (rule.getInputParameters() != null && rule.getInputParameters().containsKey("maxAge")) {
            Object maxAgeObj = rule.getInputParameters().get("maxAge");
            try {
                if (maxAgeObj instanceof String) {
                    return Long.parseLong((String) maxAgeObj);
                } else if (maxAgeObj instanceof Number) {
                    return ((Number) maxAgeObj).longValue();
                }
            } catch (Exception e) {
                log.warn("Invalid maxAge parameter for {}, using default", rule.getOutputSymbol());
            }
        }
        return 15000; // Default 15 seconds
    }
}
