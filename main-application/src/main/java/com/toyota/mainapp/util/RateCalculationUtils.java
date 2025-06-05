package com.toyota.mainapp.util;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.InputRateInfo;
import com.toyota.mainapp.dto.model.RateType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility methods for currency rate calculations
 */
@Slf4j
public final class RateCalculationUtils {

    private static final int DEFAULT_SCALE = 6;
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    private RateCalculationUtils() {

    }

    public static Optional<AverageResult> calculateAverage(
            Map<String, BaseRateDto> rates,
            int scale,
            RoundingMode rounding) {
            
        if (rates == null || rates.isEmpty()) {
            return Optional.empty();
        }
        
        BigDecimal totalBid = BigDecimal.ZERO;
        BigDecimal totalAsk = BigDecimal.ZERO;
        List<InputRateInfo> inputs = new ArrayList<>();
        int validRateCount = 0;
        long latestTimestamp = 0;
        
        for (BaseRateDto rate : rates.values()) {
            if (rate.getBid() == null || rate.getAsk() == null) {
                continue;
            }
            
            totalBid = totalBid.add(rate.getBid());
            totalAsk = totalAsk.add(rate.getAsk());
            validRateCount++;
            
            // Track latest timestamp for the result
            if (rate.getTimestamp() != null && rate.getTimestamp() > latestTimestamp) {
                latestTimestamp = rate.getTimestamp();
            }
            
            // Add to inputs list for tracking calculation sources
            inputs.add(createInputInfo(rate));
        }
        
        if (validRateCount == 0) {
            return Optional.empty();
        }
        
        // Calculate averages
        BigDecimal avgBid = totalBid.divide(BigDecimal.valueOf(validRateCount), scale, rounding);
        BigDecimal avgAsk = totalAsk.divide(BigDecimal.valueOf(validRateCount), scale, rounding);

        long resultTimestamp = latestTimestamp > 0 ? latestTimestamp : System.currentTimeMillis();
        
        return Optional.of(new AverageResult(avgBid, avgAsk, inputs, resultTimestamp, validRateCount));
    }
    
    /**
     * Calculate average with default scale and rounding
     */
    public static Optional<AverageResult> calculateAverage(Map<String, BaseRateDto> rates) {
        return calculateAverage(rates, DEFAULT_SCALE, DEFAULT_ROUNDING);
    }
    
    /**
     * Create a calculated rate from an average result
     */
    public static BaseRateDto createAverageRate(String symbol, AverageResult result, String strategyName) {
        return BaseRateDto.builder()
            .rateType(RateType.CALCULATED)
            .symbol(symbol)
            .bid(result.bid())
            .ask(result.ask())
            .timestamp(result.timestamp())
            .calculationInputs(result.inputs())
            .calculatedByStrategy(strategyName)
            .providerName("RateCalculator")
            .build();
    }
    
    /**
     * Create an input rate info from a base rate
     */
    public static InputRateInfo createInputInfo(BaseRateDto rate) {
        return new InputRateInfo(
            rate.getSymbol(),
            rate.getRateType() != null ? rate.getRateType().name() : "RAW",
            rate.getProviderName(),
            rate.getBid(),
            rate.getAsk(),
            rate.getTimestamp()
        );
    }
    
    /**
     * Check if a symbol represents a cross rate (like EUR/TRY or GBP/TRY)
     */
    public static boolean isCrossRate(String symbol) {
        if (symbol == null) return false;
        
        String normalized = symbol.toUpperCase().replace("/", "");
        return normalized.contains("TRY") && 
              (normalized.contains("EUR") || normalized.contains("GBP"));
    }
    
    /**
     * Record class to hold average calculation results
     */
    public record AverageResult(
        BigDecimal bid,
        BigDecimal ask,
        List<InputRateInfo> inputs,
        long timestamp,
        int validRateCount
    ) {}
}
