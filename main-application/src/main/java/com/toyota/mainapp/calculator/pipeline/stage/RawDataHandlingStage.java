package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ✅ STAGE 1: Raw Data Handling
 * Cache raw rate and publish to individual JSON topic
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RawDataHandlingStage implements CalculationStage {

    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;

    @Override
    public void execute(ExecutionContext context) {
        try {
            BaseRateDto triggeringRate = context.getTriggeringRate();
            String pipelineId = context.getPipelineId();
            
            log.debug("Stage 1 [{}]: Processing raw rate for symbol: {}", 
                    pipelineId, triggeringRate.getSymbol());

            // 1. Cache raw rate (already done in MainCoordinator, but ensure consistency)
            rateCacheService.cacheRawRate(triggeringRate);
            
            // 2. Publish to individual JSON topic  
            kafkaPublishingService.publishRawRate(triggeringRate);
            
            // 3. Add to context for next stages
            context.addRawRate(triggeringRate.getSymbol(), triggeringRate);
            
            log.info("Stage 1 [{}]: Raw rate processed successfully: {}", 
                    pipelineId, triggeringRate.getSymbol());

        } catch (Exception e) {
            log.error("❌ Stage 1 failed for pipeline: {}", context.getPipelineId(), e);
            // Don't throw exception - handle gracefully and continue pipeline
        }
    }
}
