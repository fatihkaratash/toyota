package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ ENHANCED: Simple batch assembly stage with immediate snapshot publishing
 * Stage 4: Publish complete snapshot with pipe-delimited format and proper sorting
 * ✅ ACTIVELY USED: Final stage in immediate pipeline processing
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
            
            // ✅ NEW: Get complete snapshot collected during pipeline execution
            Collection<BaseRateDto> allSnapshotRates = context.getSnapshotRates();
            
            if (allSnapshotRates.isEmpty()) {
                log.warn("No snapshot data available for pipeline: {}", pipelineId);
                context.recordStageEnd(stageName);
                return;
            }
            
            // ✅ NEW: Convert each rate to pipe-delimited string
            List<String> rateStrings = allSnapshotRates.stream()
                    .sorted(this::compareByTypeAndTimestamp)
                    .map(this::formatRateEntry)
                    .collect(Collectors.toList());
            
            // ✅ NEW: Publish each rate as separate message with same pipelineId
            kafkaPublishingService.publishImmediateSnapshot(rateStrings, pipelineId);
            
            // Log any stage errors for monitoring
            List<String> stageErrors = context.getStageErrors();
            if (!stageErrors.isEmpty()) {
                log.warn("Pipeline [{}] completed with {} errors: {}", 
                        pipelineId, stageErrors.size(), stageErrors);
            }
            
            // Legacy support - add stage result
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

    /**
     * Format rate entry as pipe-delimited string with proper precision
     */
    private String formatRateEntry(BaseRateDto rate) {
        String symbol = rate.getSymbol();
        
        // ✅ REMOVE CALC- prefix if present
        if (symbol.startsWith("CALC-")) {
            symbol = symbol.substring(5);
        }
        
        String identifier = (rate.getRateType() == RateType.RAW) 
                ? rate.getProviderName() + "-" + symbol
                : symbol;
        
        // ✅ FIX: Limit decimal precision to 5 places
        String formattedBid = String.format("%.5f", rate.getBid().doubleValue());
        String formattedAsk = String.format("%.5f", rate.getAsk().doubleValue());
        
        // ✅ FIX: Format timestamp as ISO date
        String formattedTimestamp = formatTimestamp(rate.getTimestamp());
                
        return String.format("%s|%s|%s|%s",
                identifier, formattedBid, formattedAsk, formattedTimestamp);
    }

    /**
     * Format timestamp as ISO string
     */
    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) {
            return Instant.now().toString();
        }
        return Instant.ofEpochMilli(timestamp).toString();
    }

    /**
     * Compare rates by type priority and timestamp
     */
    private int compareByTypeAndTimestamp(BaseRateDto a, BaseRateDto b) {
        // Priority: RAW(1) → AVG(2) → CROSS(3) → timestamp
        int priorityA = getTypePriority(a);
        int priorityB = getTypePriority(b);
        
        if (priorityA != priorityB) {
            return Integer.compare(priorityA, priorityB);
        }
        return Long.compare(a.getTimestamp(), b.getTimestamp());
    }

    /**
     * Get type priority for sorting
     */
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
