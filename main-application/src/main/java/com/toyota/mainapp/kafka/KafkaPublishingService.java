package com.toyota.mainapp.kafka;

import com.toyota.mainapp.dto.model.BaseRateDto;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Toyota Financial Data Platform - Kafka Publishing Service
 * 
 * Central service for publishing financial rate data to Kafka topics.
 * Supports immediate snapshot pipeline with proper interface implementation,
 * individual rate publishing, and topic-based message routing.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaPublishingService implements SequentialPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Qualifier("jsonKafkaTemplate")
    private final KafkaTemplate<String, Object> jsonKafkaTemplate;
    private final RateMapper rateMapper;

    private static final String RAW_RATES_TOPIC = "financial-raw-rates";
    private static final String CALCULATED_RATES_TOPIC = "financial-calculated-rates";
    private static final String SIMPLE_RATES_BATCH_TOPIC = "financial-simple-rates";


    @Override
    public void publishImmediateSnapshot(List<String> rateStrings, String pipelineId) {
        if (rateStrings == null || rateStrings.isEmpty()) {
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
                                    if (ex != null) {
                                        log.error("Failed to publish snapshot rate: {}", ex.getMessage());
                                    }
                                });
                    successCount++;
                    
                } catch (Exception e) {
                    log.error("Error publishing snapshot rate [{}]: {}", pipelineId, e.getMessage());
                    errorCount++;
                }
            }
            
            if (errorCount > 0) {
                log.warn("Immediate snapshot dispatched [{}]: {} messages sent, {} errors", 
                        pipelineId, successCount, errorCount);
            }
            
        } catch (Exception e) {
            log.error("Failed to publish immediate snapshot [{}]: {} rates - {}", 
                    pipelineId, rateStrings.size(), e.getMessage());
        }
    }
  
    @Override
    public void publishRate(BaseRateDto rate) {
        if (rate == null) return;
        
        if (isCalculatedRate(rate)) {
            publishCalculatedRate(rate);
        } else {
            publishRawRate(rate);
        }
    }

    @Override
    public void publishRawRate(BaseRateDto rawRate) {
        if (rawRate == null) {
            return;
        }

        try {
            RatePayloadDto payload = rateMapper.toRatePayloadDto(rawRate);
            payload.setEventType("RATE_RECEIVED");
            
            jsonKafkaTemplate.send(RAW_RATES_TOPIC, rawRate.getSymbol(), payload);
            
        } catch (Exception e) {
            log.error("Failed to publish raw rate: {} - {}", rawRate.getSymbol(), e.getMessage());
        }
    }

    @Override
    public void publishCalculatedRate(BaseRateDto calculatedRate) {
        if (calculatedRate == null) {
            return;
        }

        try {
            RatePayloadDto payload = rateMapper.toRatePayloadDto(calculatedRate);
            payload.setEventType("RATE_CALCULATED");
            payload.setCalculationType(determineCalculationType(calculatedRate));
            
            jsonKafkaTemplate.send(CALCULATED_RATES_TOPIC, calculatedRate.getSymbol(), payload);
            
        } catch (Exception e) {
            log.error("Failed to publish calculated rate: {} - {}", 
                    calculatedRate.getSymbol(), e.getMessage());
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

}