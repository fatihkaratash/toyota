package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 1: Process triggering raw rate and add to snapshot
 First stage in immediate pipeline processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RawDataHandlingStage implements CalculationStage {

    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;

    @Override
    public void execute(ExecutionContext context) {
        String stageName = "RawDataHandling";
        
        try {
            context.recordStageStart(stageName);
            
            BaseRateDto triggeringRate = context.getTriggeringRate();
            if (triggeringRate == null) {
                String error = "Triggering rate is null";
                context.addStageError(stageName, error);
                return;
            }

            log.debug("✅ Stage 1 [{}]: Processing raw rate: {}", 
                    context.getPipelineId(), triggeringRate.getSymbol());

            // Simple cache operation
            rateCacheService.cacheRawRate(triggeringRate);
            log.debug("Raw rate cached: {}", triggeringRate.getSymbol());

            // Publish to individual raw rate topic
            kafkaPublishingService.publishRawRate(triggeringRate);
            log.debug("Raw rate published to individual topic: {}", triggeringRate.getSymbol());

            // Add triggering rate to snapshot for immediate publishing
            context.addRateToSnapshot(triggeringRate);
            log.debug("✅ Triggering rate added to snapshot [{}]: {}", 
                    context.getPipelineId(), triggeringRate.getSymbol());

            context.addStageResult("Raw data processed: " + triggeringRate.getSymbol());

            context.recordStageEnd(stageName);
            
            log.info("✅ Stage 1 [{}]: Raw data handling completed successfully", 
                    context.getPipelineId());
            
        } catch (Exception e) {
            context.addStageError(stageName, e.getMessage());
            context.recordStageEnd(stageName);
            
            log.error("❌ Stage 1 [{}]: Raw data handling failed", 
                    context.getPipelineId(), e);
        }
    }

    @Override
    public String getStageName() {
        return "RawDataHandling";
    }
}
