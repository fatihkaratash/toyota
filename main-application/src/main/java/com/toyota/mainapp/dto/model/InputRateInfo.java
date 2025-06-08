package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ✅ INPUT RATE INFO: Calculation input metadata for Groovy scripts
 * Used in calculationInputs lists for transparency and audit trails
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputRateInfo {
    
    /**
     * Normalized symbol (e.g., "USDTRY")
     */
    private String symbol;
    
    /**
     * Rate type (RAW, CALCULATED, etc.)
     */
    private String rateType;
    
    /**
     * Provider name (e.g., "TCPProvider1", "CalculatedAVG")
     */
    private String providerName;
    
    /**
     * Bid rate value
     */
    private BigDecimal bid;
    
    /**
     * Ask rate value  
     */
    private BigDecimal ask;
    
    /**
     * Rate timestamp in milliseconds
     */
    private Long timestamp;
    
    /**
     * ✅ ENHANCED FACTORY: Create from BaseRateDto with validation
     */
    public static InputRateInfo fromBaseRateDto(BaseRateDto rate) {
        if (rate == null) {
            return null;
        }
        
        // ✅ ADD: Basic validation
        if (rate.getBid() == null || rate.getAsk() == null) {
            return null;
        }
        
        return InputRateInfo.builder()
                .symbol(rate.getSymbol())
                .rateType(rate.getRateType() != null ? rate.getRateType().toString() : "UNKNOWN")
                .providerName(rate.getProviderName())
                .bid(rate.getBid())
                .ask(rate.getAsk())
                .timestamp(rate.getTimestamp())
                .build();
    }
}
