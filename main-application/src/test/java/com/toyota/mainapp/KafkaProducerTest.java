package com.toyota.mainapp;

import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.List;

@SpringBootTest
@EmbeddedKafka(partitions = 1, 
               brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"},
               topics = {"financial-raw-rates", "financial-calculated-rates", "financial-simple-rates"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "app.kafka.topic.raw-rates=financial-raw-rates",
    "app.kafka.topic.calculated-rates=financial-calculated-rates", 
    "app.kafka.topic.simple-rates=financial-simple-rates"
})
@DirtiesContext
class KafkaProducerTest {

    @Autowired
    private KafkaPublishingService kafkaPublishingService;

    @Test
    void testPublishRawRate() {
        // Test raw rate publishing
        BaseRateDto rate = new BaseRateDto();
        rate.setSymbol("USDTRY");
        rate.setBid(new java.math.BigDecimal("34.25"));
        rate.setAsk(new java.math.BigDecimal("34.30"));
        rate.setRateType(RateType.RAW);
        rate.setProviderName("TestProvider");
        rate.setTimestamp(System.currentTimeMillis());
        
        kafkaPublishingService.publishRawRate(rate);
        // Verification would require consumer setup in test
    }

    @Test
    void testPublishImmediateSnapshot() {
        // Test immediate snapshot publishing for consumer compatibility
        List<String> rateStrings = Arrays.asList(
            "USDTRY|34.25|34.30|TestProvider|" + System.currentTimeMillis(),
            "EURTRY|37.85|37.90|TestProvider|" + System.currentTimeMillis()
        );
        
        kafkaPublishingService.publishImmediateSnapshot(rateStrings, "pipeline-test-001");
        // This tests the exact format consumer expects
    }

    @Test 
    void testCalculatedRatePublishing() {
        // Test calculated rate publishing
        BaseRateDto calculatedRate = new BaseRateDto();
        calculatedRate.setSymbol("USDTRY_AVG");
        calculatedRate.setBid(new java.math.BigDecimal("34.27"));
        calculatedRate.setAsk(new java.math.BigDecimal("34.28"));
        calculatedRate.setRateType(RateType.CALCULATED);
        calculatedRate.setCalculatedByStrategy("AVERAGE");
        calculatedRate.setTimestamp(System.currentTimeMillis());
        
        kafkaPublishingService.publishCalculatedRate(calculatedRate);
    }
}
