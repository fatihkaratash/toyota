package com.toyota.mainapp.calculator.pipeline.stage;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.kafka.KafkaPublishingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toyota Financial Data Platform - Simple Batch Assembly Stage
 * 
 * Final pipeline stage that assembles and publishes complete rate snapshots
 * with pipe-delimited format and proper sorting. Handles immediate snapshot
 * publishing for real-time rate distribution across the platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimpleBatchAssemblyStage implements CalculationStage {

    private final KafkaPublishingService kafkaPublishingService;

    @Override
    public void execute(ExecutionContext context) {
        String stageName = "SimpleBatchAssembly";
        
        try {
            context.recordStageStart(stageName);
            
            String pipelineId = context.getPipelineId();
            
            log.debug("✅ Stage 4 [{}]: Starting snapshot assembly", pipelineId);

            Collection<BaseRateDto> allSnapshotRates = context.getSnapshotRates();
            
            if (allSnapshotRates.isEmpty()) {
                log.warn("No snapshot data available for pipeline: {}", pipelineId);
                context.recordStageEnd(stageName);
                return;
            }

            List<String> rateStrings = allSnapshotRates.stream()
                    .sorted(this::compareByTypeAndTimestamp)
                    .map(this::formatRateEntry)
                    .collect(Collectors.toList());
            
            kafkaPublishingService.publishImmediateSnapshot(rateStrings, pipelineId);
            
            List<String> stageErrors = context.getStageErrors();
            if (!stageErrors.isEmpty()) {
                log.warn("Pipeline [{}] completed with {} errors: {}", 
                        pipelineId, stageErrors.size(), stageErrors);
            }
            
            // Legacy support 
            context.addStageResult(String.format("Published snapshot with %d rates", rateStrings.size()));
            
            context.recordStageEnd(stageName);
            
            log.info("✅ Stage 4 [{}]: Immediate snapshot published - {} rates, {} errors", 
                    pipelineId, rateStrings.size(), stageErrors.size());
            
        } catch (Exception e) {
            context.addStageError(stageName, "Failed to publish snapshot: " + e.getMessage());
            context.recordStageEnd(stageName);
            
            log.error("❌ Stage 4 [{}]: Snapshot publishing failed", 
                    context.getPipelineId(), e);
        }
    }

    private String formatRateEntry(BaseRateDto rate) {
        String symbol = rate.getSymbol();
        
        // REMOVE CALC- prefix if present
        if (symbol.startsWith("CALC-")) {
            symbol = symbol.substring(5);
        }
        
        String identifier = (rate.getRateType() == RateType.RAW) 
                ? rate.getProviderName() + "-" + symbol
                : symbol;
        
        String formattedBid = String.format("%.5f", rate.getBid().doubleValue());
        String formattedAsk = String.format("%.5f", rate.getAsk().doubleValue());

        String formattedTimestamp = formatTimestamp(rate.getTimestamp());
                
        return String.format("%s|%s|%s|%s",
                identifier, formattedBid, formattedAsk, formattedTimestamp);
    }

    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) {
            return Instant.now().toString();
        }
        return Instant.ofEpochMilli(timestamp).toString();
    }

    private int compareByTypeAndTimestamp(BaseRateDto a, BaseRateDto b) {
        // Priority: RAW(1) → AVG(2) → CROSS(3) → timestamp
        int priorityA = getTypePriority(a);
        int priorityB = getTypePriority(b);
        
        if (priorityA != priorityB) {
            return Integer.compare(priorityA, priorityB);
        }
        return Long.compare(a.getTimestamp(), b.getTimestamp());
    }

    private int getTypePriority(BaseRateDto rate) {
        if (rate.getRateType() == RateType.RAW) return 1;
        if (rate.getSymbol().contains("_AVG")) return 2;
        return 3; // CROSS rates
    }

    @Override
    public String getStageName() {
        return "SimpleBatchAssembly";
    }
}
