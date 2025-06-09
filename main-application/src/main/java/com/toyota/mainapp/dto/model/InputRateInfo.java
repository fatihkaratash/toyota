package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Toyota Financial Data Platform - Input Rate Information DTO
 * 
 * Lightweight data structure capturing essential rate information
 * for calculation input tracking and audit trails. Provides factory
 * methods for safe conversion from BaseRateDto with validation.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputRateInfo {

    private String symbol;
    private String rateType;
    private String providerName;
    private BigDecimal bid;
    private BigDecimal ask;
    private Long timestamp;
    
    /**
      FACTORY: Create from BaseRateDto with validation
     */
    public static InputRateInfo fromBaseRateDto(BaseRateDto rate) {
        if (rate == null) {
            return null;
        }

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
