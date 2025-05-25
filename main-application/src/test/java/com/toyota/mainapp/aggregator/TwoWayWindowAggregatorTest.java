package com.toyota.mainapp.aggregator;

import com.toyota.mainapp.calculator.RateCalculatorService;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.util.SymbolUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TwoWayWindowAggregatorTest {

    @Mock
    private RateCalculatorService rateCalculatorService;
    
    @Mock
    private TaskScheduler taskScheduler;
    
    private TwoWayWindowAggregator aggregator;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aggregator = new TwoWayWindowAggregator(taskScheduler);
        
        // Set RateCalculatorService using reflection
        try {
            java.lang.reflect.Field field = TwoWayWindowAggregator.class.getDeclaredField("rateCalculatorService");
            field.setAccessible(true);
            field.set(aggregator, rateCalculatorService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set rateCalculatorService", e);
        }
        
        // Initialize with test providers
        Map<String, List<String>> testConfig = new HashMap<>();
        testConfig.put("USDTRY", List.of("RESTProvider1", "TCPProvider2"));
        testConfig.put("EURUSD", List.of("RESTProvider1", "TCPProvider2"));
        aggregator.initialize(testConfig);
    }
    
    @Test
    void testAcceptAndProcessStandardCase() {
        // Create raw rates from two providers with acceptable time skew
        long baseTime = System.currentTimeMillis();
        
        BaseRateDto rate1 = createRawRate("RESTProvider1_USDTRY", "RESTProvider1", baseTime, "10.50", "10.55");
        BaseRateDto rate2 = createRawRate("TCPProvider2_USDTRY", "TCPProvider2", baseTime + 1000, "10.52", "10.57");
        
        // Submit rates to aggregator
        aggregator.accept(rate1);
        
        // Verify calculator not called with just one rate
        verify(rateCalculatorService, never()).processWindowCompletion(any());
        
        // Submit second rate to complete window
        aggregator.accept(rate2);
        
        // Verify calculator was called with both rates
        ArgumentCaptor<Map<String, BaseRateDto>> captor = ArgumentCaptor.forClass(Map.class);
        verify(rateCalculatorService, times(1)).processWindowCompletion(captor.capture());
        
        Map<String, BaseRateDto> processedRates = captor.getValue();
        assertEquals(2, processedRates.size());
        assertTrue(processedRates.containsKey("RESTProvider1_USDTRY"));
        assertTrue(processedRates.containsKey("TCPProvider2_USDTRY"));
    }
    
    @Test
    void testExcessiveTimeSkewPreventsCalculation() {
        // Create raw rates from two providers with excessive time skew
        long baseTime = System.currentTimeMillis();
        
        BaseRateDto rate1 = createRawRate("RESTProvider1_USDTRY", "RESTProvider1", baseTime, "10.50", "10.55");
        // Second rate with 5 second time skew (assuming max allowed is 3000ms)
        BaseRateDto rate2 = createRawRate("TCPProvider2_USDTRY", "TCPProvider2", baseTime + 5000, "10.52", "10.57");
        
        // Submit rates to aggregator
        aggregator.accept(rate1);
        aggregator.accept(rate2);
        
        // Verify calculator was not called due to excessive time skew
        verify(rateCalculatorService, never()).processWindowCompletion(any());
    }
    
    @Test
    void testMissingExpectedProviderPreventsCalculation() {
        // Create raw rate from only one provider
        long baseTime = System.currentTimeMillis();
        BaseRateDto rate = createRawRate("RESTProvider1_USDTRY", "RESTProvider1", baseTime, "10.50", "10.55");
        
        // Submit rate to aggregator
        aggregator.accept(rate);
        
        // Submit another rate but from wrong provider
        BaseRateDto wrongProviderRate = createRawRate("WrongProvider_USDTRY", "WrongProvider", baseTime, "10.52", "10.57");
        aggregator.accept(wrongProviderRate);
        
        // Verify calculator was not called since we're missing an expected provider
        verify(rateCalculatorService, never()).processWindowCompletion(any());
    }
    
    @Test
    void testCleanupRemovesStaleRates() throws Exception {
        // Create raw rates
        long oldTime = System.currentTimeMillis() - 50000; // Very old timestamp
        BaseRateDto staleRate = createRawRate("RESTProvider1_USDTRY", "RESTProvider1", oldTime, "10.50", "10.55");
        
        // Submit stale rate
        aggregator.accept(staleRate);
        
        // Manually trigger cleanup
        java.lang.reflect.Method cleanupMethod = TwoWayWindowAggregator.class.getDeclaredMethod("cleanupStaleWindows");
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(aggregator);
        
        // Submit fresh rate from second provider
        BaseRateDto freshRate = createRawRate("TCPProvider2_USDTRY", "TCPProvider2", System.currentTimeMillis(), "10.52", "10.57");
        aggregator.accept(freshRate);
        
        // Verify calculator not called since stale rate was removed by cleanup
        verify(rateCalculatorService, never()).processWindowCompletion(any());
    }
    
    @Test
    void testNonRawRateIsIgnored() {
        // Create non-raw rate
        BaseRateDto calculatedRate = BaseRateDto.builder()
                .symbol("USDTRY_AVG")
                .rateType(RateType.CALCULATED)
                .bid(new BigDecimal("10.50"))
                .ask(new BigDecimal("10.55"))
                .timestamp(System.currentTimeMillis())
                .providerName("Calculator")
                .build();
        
        // Submit non-raw rate to aggregator
        aggregator.accept(calculatedRate);
        
        // Verify calculator was not called
        verify(rateCalculatorService, never()).processWindowCompletion(any());
    }
    
    @Test
    void testCorrectSymbolDerivation() {
        // Create raw rates with different symbol formats
        long baseTime = System.currentTimeMillis();
        
        BaseRateDto rate1 = createRawRate("RESTProvider1_USD/TRY", "RESTProvider1", baseTime, "10.50", "10.55");
        BaseRateDto rate2 = createRawRate("TCPProvider2_USDTRY", "TCPProvider2", baseTime + 1000, "10.52", "10.57");
        
        // Submit rates to aggregator
        aggregator.accept(rate1);
        aggregator.accept(rate2);
        
        // Verify calculator was called (proving that both symbols were correctly derived as USDTRY)
        verify(rateCalculatorService, times(1)).processWindowCompletion(any());
    }
    
    private BaseRateDto createRawRate(String symbol, String providerName, long timestamp, String bid, String ask) {
        return BaseRateDto.builder()
                .symbol(symbol)
                .rateType(RateType.RAW)
                .bid(new BigDecimal(bid))
                .ask(new BigDecimal(ask))
                .timestamp(timestamp)
                .providerName(providerName)
                .build();
    }
}
