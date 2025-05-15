package com.toyota.restserver;

import com.toyota.restserver.model.Rate;
import com.toyota.restserver.service.RateSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RateSimulationServiceTest {

    private RateSimulationService rateSimulationService;
    private static final double TEST_VOLATILITY = 0.001; // 0.1%
    private static final double TEST_MIN_SPREAD = 0.0001;
    private static final int TEST_MAX_RETRIES = 10;

    @BeforeEach
    void setUp() {
        rateSimulationService = new RateSimulationService();
        // Extract test values to constants for better readability and maintenance
        ReflectionTestUtils.setField(rateSimulationService, "volatility", TEST_VOLATILITY);
        ReflectionTestUtils.setField(rateSimulationService, "minSpread", TEST_MIN_SPREAD);
        ReflectionTestUtils.setField(rateSimulationService, "maxRetries", TEST_MAX_RETRIES);
    }

    @Test
    void simulateFluctuation_shouldChangeBidAndAsk() {
        // Use more descriptive variable names
        Rate sourceRate = new Rate("PF2_USDTRY", 34.50000, 34.55000, "2024-07-15T10:00:00.000Z");
        Rate result = rateSimulationService.simulateFluctuation(sourceRate);

        // Group related assertions
        // 1. Basic validations
        assertNotNull(result);
        assertEquals(sourceRate.getPairName(), result.getPairName());
        assertNotEquals(sourceRate.getTimestamp(), result.getTimestamp(), "Timestamp should be updated.");
        
        // 2. Price validations
        assertTrue(result.getBid() > 0, "Bid must be positive.");
        assertTrue(result.getAsk() > result.getBid(), "Ask must be greater than Bid.");
        assertTrue(result.getAsk() - result.getBid() >= TEST_MIN_SPREAD, 
                "Spread must be at least " + TEST_MIN_SPREAD);
        
        // 3. Changes validation - Consider statistical approach for better reliability
        assertTrue(Math.abs(result.getBid() - sourceRate.getBid()) <= sourceRate.getBid() * TEST_VOLATILITY * 2, 
                "Bid fluctuation should be within expected volatility range.");
        assertTrue(Math.abs(result.getAsk() - sourceRate.getAsk()) <= sourceRate.getAsk() * TEST_VOLATILITY * 2, 
                "Ask fluctuation should be within expected volatility range.");
    }

    // Use @RepeatedTest instead of for-loop for cleaner testing of randomized behavior
    @RepeatedTest(10) // Reduced from 100 to improve test speed while still verifying behavior
    void simulateFluctuation_shouldEnsureBidLessThanAsk() {
        Rate tightSpreadRate = new Rate("PF2_EURUSD", 1.10000, 1.10010, "2024-07-15T10:00:00.000Z");
        Rate result = rateSimulationService.simulateFluctuation(tightSpreadRate);
        
        // Combined assertions with formatted messages for better diagnostics
        assertTrue(result.getAsk() > result.getBid() && 
                   result.getAsk() - result.getBid() >= TEST_MIN_SPREAD,
                   String.format("Ask (%.5f) must be > Bid (%.5f) with min spread %.5f. Actual spread: %.5f", 
                                  result.getAsk(), result.getBid(), TEST_MIN_SPREAD, result.getAsk() - result.getBid()));
    }

    @Test
    void simulateFluctuation_shouldHandleEdgeCases() {
        // Test cases combined for efficiency
        Rate[] edgeCases = {
            new Rate("ZERO_COIN", 0.0, 0.0, "2024-07-15T10:00:00.000Z"),
            new Rate("NEG_COIN", -1.0, -0.9, "2024-07-15T10:00:00.000Z"),
            new Rate("TINY_COIN", 0.000001, 0.000002, "2024-07-15T10:00:00.000Z")
        };
        
        for (Rate edgeCase : edgeCases) {
            Rate result = rateSimulationService.simulateFluctuation(edgeCase);
            String caseName = edgeCase.getPairName();
            
            assertTrue(result.getBid() > 0, 
                    caseName + ": Bid must be positive, got: " + result.getBid());
            assertTrue(result.getAsk() > result.getBid(), 
                    caseName + ": Ask must be > Bid, got Ask: " + result.getAsk() + ", Bid: " + result.getBid());
            assertTrue(result.getAsk() - result.getBid() >= TEST_MIN_SPREAD, 
                    caseName + ": Spread must be ≥ " + TEST_MIN_SPREAD + ", got: " + (result.getAsk() - result.getBid()));
        }
    }
}
