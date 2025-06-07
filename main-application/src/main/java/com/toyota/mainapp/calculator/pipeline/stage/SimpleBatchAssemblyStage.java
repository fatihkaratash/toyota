package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.calculator.pipeline.StageExecutionException;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ✅ STAGE 4: Simple batch assembly stage - STRING PIPELINE
 * Final pipeline stage that creates pipe-delimited string output
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SimpleBatchAssemblyStage implements CalculationStage {

    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;

    /**
     * ✅ STRING PIPELINE: Collect ALL rates and publish as pipe-delimited string
     */
    @Override
    public void execute(ExecutionContext context) throws StageExecutionException {
        try {
            String pipelineId = context.getPipelineId();
            BaseRateDto triggeringRate = context.getTriggeringRate();
            
            log.debug("Stage 4 [{}]: Assembling string batch for symbol: {}", 
                    pipelineId, triggeringRate.getSymbol());

            // ✅ Collect ALL current rates from Redis + Context
            StringBuilder batchString = new StringBuilder();
            
            // 1. Add triggering raw rate
            appendRateToString(batchString, triggeringRate);
            
            // 2. Get other raw rates for same symbol from cache
            Set<BaseRateDto> rawRates = rateCacheService.getRawRatesBySymbol(triggeringRate.getSymbol());
            for (BaseRateDto rate : rawRates) {
                if (!rate.getProviderName().equals(triggeringRate.getProviderName())) {
                    appendRateToString(batchString, rate);
                }
            }
            
            // 3. Add calculated rates from context (fresh calculations)
            for (BaseRateDto calculatedRate : context.getCalculatedRates()) {
                appendRateToString(batchString, calculatedRate);
            }
            
            // 4. Get additional calculated rates from cache for this symbol
            BaseRateDto avgRate = rateCacheService.getCalculatedRate(triggeringRate.getSymbol(), "AVG");
            if (avgRate != null && !isRateInContext(avgRate, context)) {
                appendRateToString(batchString, avgRate);
            }
            
            BaseRateDto crossRate = rateCacheService.getCalculatedRate(triggeringRate.getSymbol(), "CROSS");  
            if (crossRate != null && !isRateInContext(crossRate, context)) {
                appendRateToString(batchString, crossRate);
            }

            // ✅ Publish STRING batch to Kafka
            String finalBatch = batchString.toString();
            if (!finalBatch.isEmpty()) {
                // Remove trailing pipe
                if (finalBatch.endsWith("|")) {
                    finalBatch = finalBatch.substring(0, finalBatch.length() - 1);
                }
                
                kafkaPublishingService.publishStringBatch(finalBatch);
                log.info("Stage 4 [{}]: String batch published: {} chars, {} rates", 
                        pipelineId, finalBatch.length(), finalBatch.split("\\|").length / 4);
                log.debug("Stage 4 [{}]: Batch content: {}", pipelineId, finalBatch);
            } else {
                log.warn("Stage 4 [{}]: Empty batch, skipping publish", pipelineId);
            }
            
            context.addStageResult("Stage 4: String batch assembled and published");

        } catch (Exception e) {
            throw new StageExecutionException("Stage 4 failed: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Format: ProviderName-SYMBOL|BID|ASK|TIMESTAMP|
     */
    private void appendRateToString(StringBuilder sb, BaseRateDto rate) {
        if (rate == null || rate.getSymbol() == null) return;
        
        String providerName = rate.getProviderName() != null ? rate.getProviderName() : "UNKNOWN";
        String symbol = rate.getSymbol();
        String bid = rate.getBid() != null ? rate.getBid().toString() : "0.0";
        String ask = rate.getAsk() != null ? rate.getAsk().toString() : "0.0";
        long timestamp = rate.getTimestamp() != null ? rate.getTimestamp() : System.currentTimeMillis();
        
        sb.append(String.format("%s-%s|%s|%s|%d|", 
                providerName, symbol, bid, ask, timestamp));
    }
    
    /**
     * Check if rate is already in context to avoid duplicates
     */
    private boolean isRateInContext(BaseRateDto rate, ExecutionContext context) {
        return context.getCalculatedRates().stream()
                .anyMatch(contextRate -> 
                    SymbolUtils.symbolsEquivalent(rate.getSymbol(), contextRate.getSymbol()) &&
                    Objects.equals(rate.getProviderName(), contextRate.getProviderName()));
    }

    @Override
    public String getStageName() {
        return "SimpleBatchAssemblyStage";
    }

    public boolean canExecute(ExecutionContext context) {
        // Always can execute - even if no rates, we log it
        return true;
    }
}
