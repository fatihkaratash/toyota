package com.toyota.mainapp.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SymbolUtilsTest {

    @Test
    void testDeriveBaseSymbol() {
        // Raw rate format
        assertEquals("USDTRY", SymbolUtils.deriveBaseSymbol("RESTProvider1_USDTRY"));
        assertEquals("EURUSD", SymbolUtils.deriveBaseSymbol("TCPProvider2_EURUSD"));
        
        // Calculated rate format
        assertEquals("USDTRY", SymbolUtils.deriveBaseSymbol("USDTRY_AVG"));
        assertEquals("EURUSD", SymbolUtils.deriveBaseSymbol("EURUSD_CROSS"));
        
        // Currency pair format
        assertEquals("USDTRY", SymbolUtils.deriveBaseSymbol("USD/TRY"));
        assertEquals("EURUSD", SymbolUtils.deriveBaseSymbol("EURUSD"));
        
        // Edge cases
        assertEquals("", SymbolUtils.deriveBaseSymbol(""));
        assertEquals("", SymbolUtils.deriveBaseSymbol(null));
        assertEquals("_INVALID", SymbolUtils.deriveBaseSymbol("_INVALID")); // Unknown format
    }

    @Test
    void testFormatWithSlash() {
        assertEquals("USD/TRY", SymbolUtils.formatWithSlash("USDTRY"));
        assertEquals("EUR/USD", SymbolUtils.formatWithSlash("EURUSD"));
        
        // Edge cases
        assertEquals(null, SymbolUtils.formatWithSlash(null));
        assertEquals("", SymbolUtils.formatWithSlash(""));
        assertEquals("USD", SymbolUtils.formatWithSlash("USD")); // Too short
        assertEquals("USDJPY/TRY", SymbolUtils.formatWithSlash("USDJPY/TRY")); // Already has slash
    }

    @Test
    void testRemoveSlash() {
        assertEquals("USDTRY", SymbolUtils.removeSlash("USD/TRY"));
        assertEquals("EURUSD", SymbolUtils.removeSlash("EUR/USD"));
        
        // Already without slash
        assertEquals("USDTRY", SymbolUtils.removeSlash("USDTRY"));
        
        // Edge cases
        assertEquals(null, SymbolUtils.removeSlash(null));
        assertEquals("", SymbolUtils.removeSlash(""));
    }

    @Test
    void testDetermineSymbolFormat() {
        assertEquals(SymbolUtils.SymbolFormatType.RAW_RATE, 
                SymbolUtils.determineSymbolFormat("RESTProvider1_USDTRY"));
        assertEquals(SymbolUtils.SymbolFormatType.CALCULATED_RATE, 
                SymbolUtils.determineSymbolFormat("USDTRY_AVG"));
        assertEquals(SymbolUtils.SymbolFormatType.CURRENCY_PAIR, 
                SymbolUtils.determineSymbolFormat("USD/TRY"));
        assertEquals(SymbolUtils.SymbolFormatType.CURRENCY_PAIR, 
                SymbolUtils.determineSymbolFormat("USDTRY"));
        assertEquals(SymbolUtils.SymbolFormatType.UNKNOWN, 
                SymbolUtils.determineSymbolFormat("UNKNOWN-FORMAT"));
    }

    @Test
    void testExtractProviderName() {
        assertEquals("RESTProvider1", SymbolUtils.extractProviderName("RESTProvider1_USDTRY"));
        assertEquals("TCPProvider2", SymbolUtils.extractProviderName("TCPProvider2_USD/TRY"));
        
        // Edge cases
        assertNull(SymbolUtils.extractProviderName("USDTRY_AVG")); // Not a raw rate
        assertEquals("", SymbolUtils.extractProviderName("")); 
        assertEquals("", SymbolUtils.extractProviderName(null));
    }

    @Test
    void testExtractCalculationType() {
        assertEquals("AVG", SymbolUtils.extractCalculationType("USDTRY_AVG"));
        assertEquals("CROSS", SymbolUtils.extractCalculationType("EUR/USD_CROSS"));
        
        // Edge cases
        assertNull(SymbolUtils.extractCalculationType("RESTProvider1_USDTRY")); // Not a calculated rate
        assertEquals("", SymbolUtils.extractCalculationType(""));
        assertEquals("", SymbolUtils.extractCalculationType(null));
    }

    @Test
    void testCreateRawRateSymbol() {
        assertEquals("RESTProvider1_USDTRY", 
                SymbolUtils.createRawRateSymbol("RESTProvider1", "USDTRY"));
        assertEquals("TCPProvider2_EURUSD", 
                SymbolUtils.createRawRateSymbol("TCPProvider2", "EURUSD"));
    }

    @Test
    void testCreateCalculatedRateSymbol() {
        assertEquals("USDTRY_AVG", 
                SymbolUtils.createCalculatedRateSymbol("USDTRY", "AVG"));
        assertEquals("EURUSD_CROSS", 
                SymbolUtils.createCalculatedRateSymbol("EURUSD", "CROSS"));
    }
}
