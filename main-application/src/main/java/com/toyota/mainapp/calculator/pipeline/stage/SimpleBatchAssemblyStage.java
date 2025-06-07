package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ MODERNIZED: Simple batch assembly stage for string batch topic
 * Creates pipe-delimited string format for high-throughput consumption
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimpleBatchAssemblyStage implements CalculationStage {

    private final KafkaPublishingService kafkaPublishingService;

    @Value("${app.batch.include-raw:true}")
    private boolean includeRawRates;

    @Value("${app.batch.include-calculated:true}")
    private boolean includeCalculatedRates;

    @Override
    public void execute(ExecutionContext context) {
        log.debug("🔄 SimpleBatchAssemblyStage started for pipeline: {}", context.getPipelineId());

        try {
            List<String> batchEntries = new ArrayList<>();

            // ✅ CONFIGURABLE: Include raw rates if enabled
            if (includeRawRates && context.getTriggeringRate() != null) {
                String rawEntry = formatRateEntry(context.getTriggeringRate());
                if (rawEntry != null) {
                    batchEntries.add(rawEntry);
                    log.debug("Added raw rate to batch: {}", context.getTriggeringRate().getSymbol());
                }
            }

            // ✅ CONFIGURABLE: Include calculated rates if enabled
            if (includeCalculatedRates && !context.getCalculatedRates().isEmpty()) {
                List<String> calculatedEntries = context.getCalculatedRates().stream()
                        .map(this::formatRateEntry)
                        .filter(entry -> entry != null)
                        .collect(Collectors.toList());
                
                batchEntries.addAll(calculatedEntries);
                log.debug("Added {} calculated rates to batch", calculatedEntries.size());
            }

            if (batchEntries.isEmpty()) {
                log.warn("⚠️ No batch entries to publish for pipeline: {}", context.getPipelineId());
                return;
            }

            // ✅ STRING FORMAT: Create pipe-delimited batch string
            String batchString = String.join("|", batchEntries);
            
            // ✅ KAFKA: Publish to string batch topic using publishSimpleBatch
            kafkaPublishingService.publishSimpleBatch(batchString);
            
            log.info("✅ Simple batch published: {} entries, length: {} chars, pipeline: {}", 
                    batchEntries.size(), batchString.length(), context.getPipelineId());
            
            log.debug("✅ SimpleBatchAssemblyStage completed for pipeline: {}", context.getPipelineId());
            
        } catch (Exception e) {
            log.error("❌ SimpleBatchAssemblyStage failed for pipeline: {}", context.getPipelineId(), e);
        }
    }

    /**
     * ✅ FORMAT: Convert BaseRateDto to pipe-delimited string entry
     * Format: "ProviderName-Symbol|Bid|Ask|Timestamp"
     */
    private String formatRateEntry(BaseRateDto rate) {
        if (rate == null || rate.getSymbol() == null) {
            return null;
        }

        try {
            return String.format("%s-%s|%s|%s|%d",
                    rate.getProviderName() != null ? rate.getProviderName() : "Unknown",
                    rate.getSymbol(),
                    rate.getBid() != null ? rate.getBid().toString() : "0",
                    rate.getAsk() != null ? rate.getAsk().toString() : "0",
                    rate.getTimestamp() != null ? rate.getTimestamp() : System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.warn("⚠️ Failed to format rate entry: {}", rate.getSymbol(), e);
            return null;
        }
    }
}
