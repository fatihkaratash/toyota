package com.toyota.mainapp.calculator;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.calculator.pipeline.stage.*;
import com.toyota.mainapp.dto.model.BaseRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * ✅ REAL-TIME PIPELINE: Each rate triggers parallel async processing
 * 🎯 GOAL: <50ms latency, >1000 rates/sec throughput
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealTimeBatchProcessor {

    @Qualifier("pipelineTaskExecutor")
    private final TaskExecutor pipelineTaskExecutor;

    // Stage instances - stateless and thread-safe
    private final RawDataHandlingStage rawDataHandlingStage;
    private final AverageCalculationStage averageCalculationStage;
    private final CrossRateCalculationStage crossRateCalculationStage;
    private final SimpleBatchAssemblyStage simpleBatchAssemblyStage;

    /**
     * ✅ ASYNC: Each rate triggers parallel pipeline execution
     */
    @Async("pipelineTaskExecutor")
    public CompletableFuture<Void> processNewRate(BaseRateDto rawRate) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Create isolated execution context for this pipeline run
            ExecutionContext context = ExecutionContext.builder()
                    .triggeringRate(rawRate)
                    .startTime(startTime)
                    .pipelineId(generatePipelineId(rawRate))
                    .build();

            log.debug("Pipeline started: {} for symbol: {}", 
                    context.getPipelineId(), rawRate.getSymbol());

            // Sequential stage execution within this async context
            executeStages(context);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Pipeline completed: {} in {}ms", context.getPipelineId(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Pipeline failed for {}: {} ({}ms)", 
                    rawRate.getSymbol(), e.getMessage(), duration, e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Execute all stages sequentially within this pipeline instance
     */
    private void executeStages(ExecutionContext context) throws Exception {
        // Stage 1: Raw Data Handling
        rawDataHandlingStage.execute(context);
        log.debug("Stage 1 completed: {}", context.getPipelineId());

        // Stage 2: Average Calculation  
        averageCalculationStage.execute(context);
        log.debug("Stage 2 completed: {}", context.getPipelineId());

        // Stage 3: Cross Rate Calculation
        crossRateCalculationStage.execute(context);
        log.debug("Stage 3 completed: {}", context.getPipelineId());

        // Stage 4: String Batch Assembly (FINAL)
        simpleBatchAssemblyStage.execute(context);
        log.debug("Stage 4 completed: {}", context.getPipelineId());
    }

    private String generatePipelineId(BaseRateDto rate) {
        return String.format("PIPE_%s_%s_%d", 
                rate.getSymbol(), rate.getProviderName(), System.currentTimeMillis());
    }
}
