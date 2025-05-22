package com.toyota.mainapp.util;

import com.toyota.mainapp.dto.common.CurrencyPairDto;

/**
 * Utility class for parsing currency pair symbols
 */
public class CurrencyPairParser {
    
    /**
     * Parse a symbol string into a CurrencyPairDto
     * 
     * Handles formats like:
     * - "EURUSD" (no separator)
     * - "EUR/USD" (slash separator)
     * - "EUR_USD" (underscore separator)
     * 
     * @param symbol The symbol string to parse
     * @return A CurrencyPairDto containing the base and quote currencies
     * @throws IllegalArgumentException if the symbol format is invalid
     */
    public static CurrencyPairDto parse(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        
        // Remove any whitespace
        symbol = symbol.trim();
        
        // Handle different formats
        if (symbol.length() == 6 && !symbol.contains("/") && !symbol.contains("_")) {
            // Format: "EURUSD" - 3 chars each
            return new CurrencyPairDto(
                symbol.substring(0, 3),
                symbol.substring(3, 6)
            );
        } else if (symbol.contains("/")) {
            // Format: "EUR/USD"
            String[] parts = symbol.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Symbol with '/' must have exactly two parts");
            }
            return new CurrencyPairDto(parts[0].trim(), parts[1].trim());
        } else if (symbol.contains("_")) {
            // Format: "EUR_USD"
            String[] parts = symbol.split("_");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Symbol with '_' must have exactly two parts");
            }
            return new CurrencyPairDto(parts[0].trim(), parts[1].trim());
        }
        
        // If we got here, we couldn't parse the symbol
        throw new IllegalArgumentException("Symbol format not recognized: " + symbol);
    }
}
