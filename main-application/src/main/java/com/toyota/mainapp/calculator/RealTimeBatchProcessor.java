package com.toyota.mainapp.calculator;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.calculator.pipeline.stage.*;
import com.toyota.mainapp.config.ApplicationProperties;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j  
@RequiredArgsConstructor
public class RealTimeBatchProcessor {

    private final RawDataHandlingStage rawDataHandlingStage;
    private final AverageCalculationStage averageCalculationStage;
    private final CrossRateCalculationStage crossRateCalculationStage;
    private final SimpleBatchAssemblyStage simpleBatchAssemblyStage;
    
    private final ApplicationProperties applicationProperties;

    @Async("pipelineTaskExecutor")
    public CompletableFuture<Void> processNewRate(BaseRateDto rawRate) {
        long startTime = System.currentTimeMillis();
        String pipelineId = SymbolUtils.generatePipelineId(rawRate);
        
        try {
            if (!applicationProperties.isConfigurationReady()) {
                log.warn("Pipeline [{}]: Configuration not ready, skipping", pipelineId);
                return CompletableFuture.completedFuture(null);
            }
            
            ExecutionContext context = ExecutionContext.builder()
                    .triggeringRate(rawRate)
                    .startTime(startTime)
                    .pipelineId(pipelineId)
                    .build();

            log.debug("Pipeline [{}]: Started for {}", pipelineId, rawRate.getSymbol());

            runPipelineStages(context);

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Pipeline [{}]: Completed in {}ms with {} snapshot rates", 
                    pipelineId, duration, context.getSnapshotRates().size());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ Pipeline [{}]: Failed after {}ms - {}", pipelineId, duration, e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void runPipelineStages(ExecutionContext context) {
        // Stage 1: Process raw rate (add triggering rate to snapshot)
        rawDataHandlingStage.execute(context);
        log.info("Pipeline [{}]: Stage 1 completed - {} rates in snapshot", 
                context.getPipelineId(), context.getSnapshotRates().size());

        // Stage 2: Calculate averages (collect inputs from cache, add to snapshot)
        averageCalculationStage.execute(context);
        log.info("Pipeline [{}]: Stage 2 completed - {} rates in snapshot", 
                context.getPipelineId(), context.getSnapshotRates().size());
        
        //  DEBUG: Log snapshot contents before cross-rate stage
        log.debug("📋 Snapshot before CROSS stage [{}]: {}", 
                context.getPipelineId(), 
                context.getSnapshotRates().stream()
                    .map(rate -> rate.getSymbol() + "(" + rate.getRateType() + ")")
                    .toList());

        // Stage 3: Calculate cross rates (use snapshot data only)
        crossRateCalculationStage.execute(context);
        log.info("Pipeline [{}]: Stage 3 completed - {} rates in snapshot", 
                context.getPipelineId(), context.getSnapshotRates().size());

        // Stage 4: Publish complete snapshot
        simpleBatchAssemblyStage.execute(context);
        log.info("Pipeline [{}]: Stage 4 completed - snapshot published", 
                context.getPipelineId());
    }
}
