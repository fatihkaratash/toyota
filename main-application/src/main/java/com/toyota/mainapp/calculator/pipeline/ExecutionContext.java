package com.toyota.mainapp.calculator.pipeline;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Toyota Financial Data Platform - Pipeline Execution Context
 * 
 * Maintains state and provides coordination during rate calculation pipeline
 * execution. Tracks input rates, intermediate results, timing metrics, and
 * error conditions across multiple processing stages.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Data
@Builder
@Slf4j
public class ExecutionContext {
    
    private String pipelineId;
    private long startTime;
    private BaseRateDto triggeringRate;
    
    @Builder.Default
    private Map<String, BaseRateDto> rawRates = new HashMap<>();
    
    @Builder.Default
    private List<BaseRateDto> calculatedRates = new ArrayList<>();
    
    @Builder.Default  
    private List<String> stageResults = new ArrayList<>();
    
    @Builder.Default
    private final Map<String, BaseRateDto> snapshotRates = new ConcurrentHashMap<>();
    
    @Builder.Default
    private final List<String> stageErrors = new ArrayList<>();
    
    @Builder.Default
    private final Map<String, Long> stageTimings = new HashMap<>();
    
    public void addRateToSnapshot(BaseRateDto rate) {
        if (rate == null) return;
        
        String key = SymbolUtils.generateSnapshotKey(rate);
        snapshotRates.put(key, rate);
        log.debug("Added to snapshot [{}]: {}", pipelineId, key);
    }
    
    public void addAllRatesToSnapshot(Collection<BaseRateDto> rates) {
        if (rates != null) {
            rates.forEach(this::addRateToSnapshot);
            log.debug("Added {} rates to snapshot [{}]", rates.size(), pipelineId);
        }
    }
    
    public Collection<BaseRateDto> getSnapshotRates() {
        return snapshotRates.values();
    }

    public void addStageError(String stageName, String error) {
        String errorEntry = stageName + ": " + error;
        stageErrors.add(errorEntry);
        log.warn("Stage error [{}] {}: {}", pipelineId, stageName, error);
    }

    public List<String> getStageErrors() {
        return new ArrayList<>(stageErrors);
    }
 
    public void recordStageStart(String stageName) {
        stageTimings.put(stageName + "_start", System.currentTimeMillis());
    }

    public void recordStageEnd(String stageName) {
        stageTimings.put(stageName + "_end", System.currentTimeMillis());
        
        Long startTime = stageTimings.get(stageName + "_start");
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            stageTimings.put(stageName + "_duration", duration);
            log.debug("Stage [{}] {} completed in {}ms", pipelineId, stageName, duration);
        }
    }

    public Map<String, Object> getPipelineStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pipelineId", pipelineId);
        stats.put("totalDuration", System.currentTimeMillis() - startTime);
        stats.put("snapshotRatesCount", snapshotRates.size());
        stats.put("stageErrorsCount", stageErrors.size());
        stats.put("stageDurations", getStagesDurations());
        return stats;
    }
    
    private Map<String, Long> getStagesDurations() {
        Map<String, Long> durations = new HashMap<>();
        stageTimings.entrySet().stream()
            .filter(entry -> entry.getKey().endsWith("_duration"))
            .forEach(entry -> {
                String stageName = entry.getKey().replace("_duration", "");
                durations.put(stageName, entry.getValue());
            });
        return durations;
    }
    
    public void addStageResult(String result) {
        this.stageResults.add(result);
    }

    public List<BaseRateDto> getCalculatedRates() {
        return new ArrayList<>(calculatedRates);
    }
}
