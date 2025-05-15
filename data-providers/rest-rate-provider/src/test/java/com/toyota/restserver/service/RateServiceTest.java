package com.toyota.restserver.service;

import com.toyota.restserver.exception.RateNotFoundException;
import com.toyota.restserver.model.Rate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateServiceTest {

    @Mock
    private RateConfigLoader rateConfigLoader;

    @Mock
    private RateSimulationService rateSimulationService;

    @InjectMocks
    private RateService rateService;

    private Rate baseRate;
    private Rate fluctuatedRate;

    @BeforeEach
    void setUp() {
        baseRate = new Rate("PF2_USDTRY", 34.50, 34.55, "2024-07-15T10:00:00.000Z");
        fluctuatedRate = new Rate("PF2_USDTRY", 34.51, 34.56, "2024-07-15T10:00:01.000Z"); // Example fluctuated
    }

    @Test
    void getRateByName_whenRateExists_shouldReturnFluctuatedRate() {
        when(rateConfigLoader.getInitialRate("PF2_USDTRY")).thenReturn(baseRate);
        when(rateSimulationService.simulateFluctuation(baseRate)).thenReturn(fluctuatedRate);

        Rate result = rateService.getRateByName("PF2_USDTRY");

        assertNotNull(result);
        assertEquals("PF2_USDTRY", result.getPairName());
        assertEquals(fluctuatedRate.getBid(), result.getBid());
        assertEquals(fluctuatedRate.getAsk(), result.getAsk());
        assertNotEquals(baseRate.getTimestamp(), result.getTimestamp()); // Timestamp should be updated

        verify(rateConfigLoader).getInitialRate("PF2_USDTRY");
        verify(rateSimulationService).simulateFluctuation(baseRate);
    }

    @Test
    void getRateByName_whenRateNotFound_shouldThrowRateNotFoundException() {
        when(rateConfigLoader.getInitialRate("UNKNOWN")).thenReturn(null);

        assertThrows(RateNotFoundException.class, () -> {
            rateService.getRateByName("UNKNOWN");
        });

        verify(rateConfigLoader).getInitialRate("UNKNOWN");
        verifyNoInteractions(rateSimulationService);
    }
}
