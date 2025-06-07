package com.toyota.mainapp.kafka;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.SimpleRateDto;
import com.toyota.mainapp.dto.model.SimpleRatesBatchDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.dto.kafka.RatePayloadDto;
import com.toyota.mainapp.mapper.RateMapper;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * ✅ CLEANED: Kafka publishing service - duplicates removed
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaPublishingService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Qualifier("jsonKafkaTemplate")
    private final KafkaTemplate<String, Object> jsonKafkaTemplate;
    private final RateMapper rateMapper;

    // ✅ Updated topic names for real-time pipeline
    private static final String RAW_RATES_TOPIC = "financial-raw-rates";
    private static final String CALCULATED_RATES_TOPIC = "financial-calculated-rates";
    private static final String SIMPLE_RATES_BATCH_TOPIC = "financial-simple-rates"; // ✅ STRING topic

    /**
     * ✅ Publish raw rate to individual topic
     */
    public void publishRawRate(BaseRateDto rawRate) {
        if (rawRate == null) {
            log.warn("Null raw rate, publishing skipped");
            return;
        }

        try {
            RatePayloadDto payload = rateMapper.toRatePayloadDto(rawRate);
            payload.setEventType("RATE_RECEIVED");
            
            kafkaTemplate.send(RAW_RATES_TOPIC, rawRate.getSymbol(), payload);
            log.debug("Raw rate published: {} to {}", rawRate.getSymbol(), RAW_RATES_TOPIC);
            
        } catch (Exception e) {
            log.error("Failed to publish raw rate: {} - {}", rawRate.getSymbol(), e.getMessage(), e);
        }
    }

    /**
     * ✅ Publish calculated rate to individual topic
     */
    public void publishCalculatedRate(BaseRateDto calculatedRate) {
        if (calculatedRate == null) {
            log.warn("Null calculated rate, publishing skipped");
            return;
        }

        try {
            RatePayloadDto payload = rateMapper.toRatePayloadDto(calculatedRate);
            payload.setEventType("RATE_CALCULATED");
            payload.setCalculationType(determineCalculationType(calculatedRate));
            
            kafkaTemplate.send(CALCULATED_RATES_TOPIC, calculatedRate.getSymbol(), payload);
            log.debug("Calculated rate published: {} ({}) to {}", 
                    calculatedRate.getSymbol(), payload.getCalculationType(), CALCULATED_RATES_TOPIC);
            
        } catch (Exception e) {
            log.error("Failed to publish calculated rate: {} - {}", 
                    calculatedRate.getSymbol(), e.getMessage(), e);
        }
    }

    /**
     * ✅ Publish simple rates batch
     */
    public void publishSimpleRatesBatch(List<BaseRateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            log.debug("Empty rates batch, publishing skipped");
            return;
        }

        try {
            List<SimpleRateDto> simpleRates = rates.stream()
                    .map(rate -> this.toSimpleRateDto(rate))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (simpleRates.isEmpty()) {
                log.warn("No valid simple rates after conversion");
                return;
            }

            SimpleRatesBatchDto batchDto = SimpleRatesBatchDto.builder()
                    .batchId(generateBatchId())
                    .timestamp(System.currentTimeMillis())
                    .rates(simpleRates)
                    .rateCount(simpleRates.size())
                    .build();

            kafkaTemplate.send(SIMPLE_RATES_BATCH_TOPIC, batchDto.getBatchId(), batchDto);
            log.info("Simple rates batch published: {} rates to {}", 
                    simpleRates.size(), SIMPLE_RATES_BATCH_TOPIC);
            
        } catch (Exception e) {
            log.error("Failed to publish simple rates batch: {} rates - {}", 
                    rates.size(), e.getMessage(), e);
        }
    }

    /**
     * ✅ LEGACY SUPPORT: Keep for backward compatibility
     */
    public void publishRate(BaseRateDto rate) {
        if (rate == null) return;
        
        if (isCalculatedRate(rate)) {
            publishCalculatedRate(rate);
        } else {
            publishRawRate(rate);
        }
    }

    // HELPER METHODS
    private String determineCalculationType(BaseRateDto rate) {
        return SymbolUtils.determineCalculationType(rate.getSymbol(), rate.getCalculatedByStrategy());
    }

    private boolean isCalculatedRate(BaseRateDto rate) {
        return rate.getRateType() == RateType.CALCULATED || 
               rate.getSymbol().contains("AVG") || 
               rate.getSymbol().contains("CROSS");
    }

    private SimpleRateDto toSimpleRateDto(BaseRateDto rate) {
        try {
            return SimpleRateDto.builder()
                    .symbol(rate.getSymbol())
                    .bid(rate.getBid())
                    .ask(rate.getAsk())
                    .timestamp(rate.getTimestamp())
                    .providerName(rate.getProviderName())
                    .rateType(rate.getRateType() != null ? rate.getRateType().name() : "RAW")
                    .build();
        } catch (Exception e) {
            log.error("Failed to convert to SimpleRateDto: {} - {}", rate.getSymbol(), e.getMessage());
            return null;
        }
    }

    private String generateBatchId() {
        return "BATCH_" + System.currentTimeMillis() + "_" + 
               ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /**
     * ✅ Enhanced JSON publishing for individual rates
     */
    public void publishRawJson(BaseRateDto rawRate) {
        if (rawRate == null) return;
        
        try {
            RatePayloadDto payload = rateMapper.toRatePayloadDto(rawRate);
            payload.setEventType("RATE_RECEIVED");
            
            jsonKafkaTemplate.send(RAW_RATES_TOPIC, rawRate.getSymbol(), payload);
            log.debug("Raw JSON published: {} to {}", rawRate.getSymbol(), RAW_RATES_TOPIC);
        } catch (Exception e) {
            log.error("Failed to publish raw JSON: {} - {}", rawRate.getSymbol(), e.getMessage(), e);
        }
    }

    /**
     * ✅ Enhanced JSON publishing for calculated rates
     */
    public void publishCalculatedJson(BaseRateDto calculatedRate) {
        if (calculatedRate == null) return;
        
        try {
            RatePayloadDto payload = rateMapper.toRatePayloadDto(calculatedRate);
            payload.setEventType("RATE_CALCULATED");
            payload.setCalculationType(determineCalculationType(calculatedRate));
            
            jsonKafkaTemplate.send(CALCULATED_RATES_TOPIC, calculatedRate.getSymbol(), payload);
            log.debug("Calculated JSON published: {} ({}) to {}", 
                    calculatedRate.getSymbol(), payload.getCalculationType(), CALCULATED_RATES_TOPIC);
        } catch (Exception e) {
            log.error("Failed to publish calculated JSON: {} - {}", 
                    calculatedRate.getSymbol(), e.getMessage(), e);
        }
    }

    /**
     * ✅ NEW: Publish string batch (pipe-delimited format)
     */
    public void publishStringBatch(String batchString) {
        if (batchString == null || batchString.trim().isEmpty()) {
            log.debug("Empty string batch, publishing skipped");
            return;
        }

        try {
            // Use string key for distribution
            String batchKey = "BATCH_" + System.currentTimeMillis();
            
            kafkaTemplate.send(SIMPLE_RATES_BATCH_TOPIC, batchKey, batchString);
            log.info("String batch published: {} chars to {}", batchString.length(), SIMPLE_RATES_BATCH_TOPIC);
            log.debug("Batch content: {}", batchString);
            
        } catch (Exception e) {
            log.error("Failed to publish string batch: {} chars - {}", 
                    batchString.length(), e.getMessage(), e);
        }
    }

    /**
     * ✅ ALTERNATIVE: Publish simple batch (for compatibility) - ALIAS
     */
    public void publishSimpleBatch(String batchString) {
        publishStringBatch(batchString);
    }
}