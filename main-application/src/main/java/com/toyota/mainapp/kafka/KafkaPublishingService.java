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

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * ✅ ENHANCED: Kafka publishing service implementing SequentialPublisher
 * Supports immediate snapshot pipeline with proper interface implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaPublishingService implements SequentialPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Qualifier("jsonKafkaTemplate")
    private final KafkaTemplate<String, Object> jsonKafkaTemplate;
    private final RateMapper rateMapper;

    // ✅ Updated topic names for real-time pipeline
    private static final String RAW_RATES_TOPIC = "financial-raw-rates";
    private static final String CALCULATED_RATES_TOPIC = "financial-calculated-rates";
    private static final String SIMPLE_RATES_BATCH_TOPIC = "financial-simple-rates";

    /**
     * ✅ NEW: Publish immediate snapshot as separate messages with same pipelineId
     * CRITICAL: Each rate as separate message, pipelineId as KEY for consumer grouping
     */
    @Override
    public void publishImmediateSnapshot(List<String> rateStrings, String pipelineId) {
        if (rateStrings == null || rateStrings.isEmpty()) {
            log.debug("Empty rate strings for immediate snapshot, publishing skipped");
            return;
        }
        
        if (pipelineId == null || pipelineId.trim().isEmpty()) {
            log.warn("Invalid pipelineId for immediate snapshot: {}", pipelineId);
            return;
        }

        try {
            int successCount = 0;
            int errorCount = 0;
            
            for (String rateString : rateStrings) {
                try {
                    // Each rate as separate message, pipelineId as KEY for consumer grouping
                    kafkaTemplate.send(SIMPLE_RATES_BATCH_TOPIC, pipelineId, rateString)
                                .whenComplete((result, ex) -> {
                                    if (ex == null) {
                                        log.debug("Snapshot rate published: {}", rateString);
                                    } else {
                                        log.error("Failed to publish snapshot rate: {} - {}", rateString, ex.getMessage());
                                    }
                                });
                    successCount++;
                    
                } catch (Exception e) {
                    log.error("Error publishing snapshot rate [{}]: {} - {}", 
                            pipelineId, rateString, e.getMessage(), e);
                    errorCount++;
                }
            }
            
            log.info("✅ Immediate snapshot dispatched [{}]: {} messages sent, {} errors", 
                    pipelineId, successCount, errorCount);
            
        } catch (Exception e) {
            log.error("Failed to publish immediate snapshot [{}]: {} rates - {}", 
                    pipelineId, rateStrings.size(), e.getMessage(), e);
        }
    }

    /**
     * ✅ LEGACY SUPPORT: Publish individual rate (auto-detect type)
     */
    @Override
    public void publishRate(BaseRateDto rate) {
        if (rate == null) return;
        
        if (isCalculatedRate(rate)) {
            publishCalculatedRate(rate);
        } else {
            publishRawRate(rate);
        }
    }

    /**
     * ✅ Publish raw rate to individual JSON topic
     */
    @Override
    public void publishRawRate(BaseRateDto rawRate) {
        if (rawRate == null) {
            log.warn("Null raw rate, publishing skipped");
            return;
        }

        try {
            RatePayloadDto payload = rateMapper.toRatePayloadDto(rawRate);
            payload.setEventType("RATE_RECEIVED");
            
            jsonKafkaTemplate.send(RAW_RATES_TOPIC, rawRate.getSymbol(), payload);
            log.debug("Raw rate published: {} to {}", rawRate.getSymbol(), RAW_RATES_TOPIC);
            
        } catch (Exception e) {
            log.error("Failed to publish raw rate: {} - {}", rawRate.getSymbol(), e.getMessage(), e);
        }
    }

    /**
     * ✅ Publish calculated rate to individual JSON topic
     */
    @Override
    public void publishCalculatedRate(BaseRateDto calculatedRate) {
        if (calculatedRate == null) {
            log.warn("Null calculated rate, publishing skipped");
            return;
        }

        try {
            RatePayloadDto payload = rateMapper.toRatePayloadDto(calculatedRate);
            payload.setEventType("RATE_CALCULATED");
            payload.setCalculationType(determineCalculationType(calculatedRate));
            
            jsonKafkaTemplate.send(CALCULATED_RATES_TOPIC, calculatedRate.getSymbol(), payload);
            log.debug("Calculated rate published: {} ({}) to {}", 
                    calculatedRate.getSymbol(), payload.getCalculationType(), CALCULATED_RATES_TOPIC);
            
        } catch (Exception e) {
            log.error("Failed to publish calculated rate: {} - {}", 
                    calculatedRate.getSymbol(), e.getMessage(), e);
        }
    }

    /**
     * ✅ LEGACY: Publish simple rates batch
     */
    @Override
    public void publishSimpleRatesBatch(List<BaseRateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            log.debug("Empty rates batch, publishing skipped");
            return;
        }

        try {
            List<SimpleRateDto> simpleRates = rates.stream()
                    .map(this::toSimpleRateDto)
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
     * ✅ LEGACY: Publish string batch (pipe-delimited format)
     */
    @Override
    public void publishStringBatch(String batchString) {
        if (batchString == null || batchString.trim().isEmpty()) {
            log.debug("Empty string batch, publishing skipped");
            return;
        }

        try {
            String batchKey = "BATCH_" + System.currentTimeMillis();
            
            kafkaTemplate.send(SIMPLE_RATES_BATCH_TOPIC, batchKey, batchString);
            log.info("String batch published: {} chars to {}", batchString.length(), SIMPLE_RATES_BATCH_TOPIC);
            log.debug("Batch content: {}", batchString);
            
        } catch (Exception e) {
            log.error("Failed to publish string batch: {} chars - {}", 
                    batchString.length(), e.getMessage(), e);
        }
    }

    // ✅ HELPER METHODS
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
            // ✅ ADD: Validate rate values before conversion
            if (rate.getBid() == null || rate.getAsk() == null ||
                rate.getBid().compareTo(BigDecimal.ZERO) <= 0 ||
                rate.getAsk().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid rate values for {}: bid={}, ask={}", 
                        rate.getSymbol(), rate.getBid(), rate.getAsk());
                return null;
            }
            
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

    // ✅ DEPRECATED METHODS - Keeping for backward compatibility
    /**
     * @deprecated Use publishRawRate() instead
     */
    @Deprecated
    public void publishRawJson(BaseRateDto rawRate) {
        publishRawRate(rawRate);
    }

    /**
     * @deprecated Use publishCalculatedRate() instead
     */
    @Deprecated
    public void publishCalculatedJson(BaseRateDto calculatedRate) {
        publishCalculatedRate(calculatedRate);
    }

    /**
     * @deprecated Use publishStringBatch() instead
     */
    @Deprecated
    public void publishSimpleBatch(String batchString) {
        publishStringBatch(batchString);
    }
}