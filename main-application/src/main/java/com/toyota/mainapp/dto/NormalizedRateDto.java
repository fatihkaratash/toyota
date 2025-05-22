package com.toyota.mainapp.dto;

import com.toyota.mainapp.dto.common.CurrencyPairDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents rate data that has been normalized to standard formats and data types
 * but has not yet been validated or processed further.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedRateDto {
    
    /**
     * The symbol as provided by the rate provider (e.g., "EURUSD", "USD/TRY")
     */
    private String symbol;
    
    /**
     * Normalized bid price as BigDecimal
     */
    private BigDecimal bid;
    
    /**
     * Normalized ask price as BigDecimal
     */
    private BigDecimal ask;
    
    /**
     * Normalized timestamp in UTC epoch milliseconds
     */
    private long timestamp;
    
    /**
     * Name of the provider this rate came from
     */
    private String providerName;
    
    /**
     * Parsed currency pair representation
     */
    private CurrencyPairDto currencyPair;

    /**
     * Normalized last price as BigDecimal
     */
    private BigDecimal last;
}
