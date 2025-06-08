package com.toyota.mainapp.util;

import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.InputRateInfo;
import com.toyota.mainapp.dto.model.RateType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * ✅ COMPLETE: Rate calculation utilities for AVG and statistical operations
 */
@Slf4j
public class RateCalculationUtils {

    private static final int DEFAULT_SCALE = 5;
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    /**
     * ✅ Calculate average from multiple rate inputs with validation
     */
    public static BaseRateDto calculateAverage(CalculationRuleDto rule, Map<String, BaseRateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            log.warn("No rates provided for average calculation: {}", rule.getOutputSymbol());
            return null;
        }

        BigDecimal totalBid = BigDecimal.ZERO;
        BigDecimal totalAsk = BigDecimal.ZERO;
        int validRateCount = 0;
        long latestTimestamp = 0;
        List<InputRateInfo> inputs = new ArrayList<>();

        for (BaseRateDto rate : rates.values()) {
            if (rate.getBid() == null || rate.getAsk() == null) {
                continue;
            }
            
            // ✅ ADD: Validate bid/ask are reasonable values
            if (rate.getBid().compareTo(BigDecimal.ZERO) <= 0 || 
                rate.getAsk().compareTo(BigDecimal.ZERO) <= 0) {
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
            log.warn("No valid rates found for average calculation: {}", rule.getOutputSymbol());
            return null;
        }

        // Calculate averages with proper precision
        BigDecimal avgBid = totalBid.divide(BigDecimal.valueOf(validRateCount), DEFAULT_SCALE, DEFAULT_ROUNDING);
        BigDecimal avgAsk = totalAsk.divide(BigDecimal.valueOf(validRateCount), DEFAULT_SCALE, DEFAULT_ROUNDING);

        // Use current time if no valid timestamp found
        if (latestTimestamp == 0) {
            latestTimestamp = System.currentTimeMillis();
        }

        log.info("✅ Average calculated for {}: bid={}, ask={} from {} rates", 
                rule.getOutputSymbol(), avgBid, avgAsk, validRateCount);

        return BaseRateDto.builder()
                .symbol(rule.getOutputSymbol())
                .bid(avgBid)
                .ask(avgAsk)
                .timestamp(latestTimestamp)
                .rateType(RateType.CALCULATED)
                .providerName("AvgCalculator")
                .calculationInputs(inputs)
                .build();
    }

    /**
     * ✅ Create input info for calculation tracking
     */
    public static InputRateInfo createInputInfo(BaseRateDto rate) {
        return InputRateInfo.fromBaseRateDto(rate);
    }

    /**
     * ✅ Validate if rate values are reasonable
     */
    public static boolean isValidRate(BaseRateDto rate) {
        if (rate == null || rate.getBid() == null || rate.getAsk() == null) {
            return false;
        }
        
        return rate.getBid().compareTo(BigDecimal.ZERO) > 0 && 
               rate.getAsk().compareTo(BigDecimal.ZERO) > 0 &&
               rate.getAsk().compareTo(rate.getBid()) >= 0; // Ask >= Bid
    }

    /**
     * ✅ Calculate weighted average (if needed in future)
     */
    public static BaseRateDto calculateWeightedAverage(CalculationRuleDto rule, 
                                                      Map<String, BaseRateDto> rates,
                                                      Map<String, BigDecimal> weights) {
        // Implementation for weighted averages if needed
        return calculateAverage(rule, rates); // Fallback to simple average for now
    }
}
