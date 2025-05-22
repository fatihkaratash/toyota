package com.toyota.mainapp.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a currency pair (e.g., EUR/USD) with clearly identified
 * base and quote currencies.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyPairDto {
    
    /**
     * Base currency (e.g., "EUR" in "EUR/USD")
     */
    private String baseCurrency;
    
    /**
     * Quote currency (e.g., "USD" in "EUR/USD") 
     */
    private String quoteCurrency;
    
    @Override
    public String toString() {
        return baseCurrency + "/" + quoteCurrency;
    }
}
