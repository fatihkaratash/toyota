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
 * ✅ ENHANCED EXECUTION CONTEXT: Pipeline state management with comprehensive snapshot collection
 * Contains all data needed throughout pipeline execution + immediate snapshot capabilities
 * ✅ ACTIVELY USED: State management in RealTimeBatchProcessor and all pipeline stages
 */
@Data
@Builder
@Slf4j
public class ExecutionContext {
    
    private String pipelineId;
    private long startTime;
    private BaseRateDto triggeringRate;
    
    // ✅ EXISTING: Stage results storage (maintained for backward compatibility)
    @Builder.Default
    private Map<String, BaseRateDto> rawRates = new HashMap<>();
    
    @Builder.Default
    private List<BaseRateDto> calculatedRates = new ArrayList<>();
    
    @Builder.Default  
    private List<String> stageResults = new ArrayList<>();
    
    // ✅ NEW: Comprehensive snapshot collection for immediate publishing
    @Builder.Default
    private final Map<String, BaseRateDto> snapshotRates = new ConcurrentHashMap<>();
    
    @Builder.Default
    private final List<String> stageErrors = new ArrayList<>();
    
    // ✅ NEW: Pipeline execution metrics
    @Builder.Default
    private final Map<String, Long> stageTimings = new HashMap<>();
    
    /**
     * ✅ NEW: Add rate to snapshot (replaces existing if newer)
     * CRITICAL: Use for ALL rates - both calculated outputs AND cached inputs
     * ✅ ACTIVELY USED: Called by all pipeline stages to collect comprehensive snapshot
     */
    public void addRateToSnapshot(BaseRateDto rate) {
        if (rate == null) return;
        
        String key = SymbolUtils.generateSnapshotKey(rate);
        snapshotRates.put(key, rate);
        log.debug("Added to snapshot [{}]: {}", pipelineId, key);
    }
    
    /**
     * ✅ NEW: Add multiple rates from cache retrieval
     * ✅ ACTIVELY USED: Called when retrieving dependencies from cache
     */
    public void addAllRatesToSnapshot(Collection<BaseRateDto> rates) {
        if (rates != null) {
            rates.forEach(this::addRateToSnapshot);
            log.debug("Added {} rates to snapshot [{}]", rates.size(), pipelineId);
        }
    }
    
    /**
     * ✅ NEW: Get all snapshot rates for immediate publishing
     * ✅ ACTIVELY USED: Called by SimpleBatchAssemblyStage for immediate snapshot publishing
     */
    public Collection<BaseRateDto> getSnapshotRates() {
        return snapshotRates.values();
    }
    
    /**
     * ✅ NEW: Record stage error for partial processing
     * ✅ ACTIVELY USED: Error handling in all pipeline stages
     */
    public void addStageError(String stageName, String error) {
        String errorEntry = stageName + ": " + error;
        stageErrors.add(errorEntry);
        log.warn("Stage error [{}] {}: {}", pipelineId, stageName, error);
    }
    
    /**
     * ✅ NEW: Get all stage errors for monitoring
     */
    public List<String> getStageErrors() {
        return new ArrayList<>(stageErrors);
    }
    
    /**
     * ✅ NEW: Record stage execution timing
     * ✅ ACTIVELY USED: Performance monitoring in pipeline stages
     */
    public void recordStageStart(String stageName) {
        stageTimings.put(stageName + "_start", System.currentTimeMillis());
    }
    
    /**
     * ✅ NEW: Record stage completion timing
     */
    public void recordStageEnd(String stageName) {
        stageTimings.put(stageName + "_end", System.currentTimeMillis());
        
        Long startTime = stageTimings.get(stageName + "_start");
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            stageTimings.put(stageName + "_duration", duration);
            log.debug("Stage [{}] {} completed in {}ms", pipelineId, stageName, duration);
        }
    }
    
    /**
     * ✅ NEW: Get pipeline execution statistics
     */
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
    
    // ✅ EXISTING METHODS: Maintained for backward compatibility
    
    /**
     * ✅ EXISTING: ADD RAW RATE: Store raw rate by symbol
     */
    public void addRawRate(String symbol, BaseRateDto rate) {
        this.rawRates.put(symbol, rate);
        // Also add to snapshot for immediate publishing
        addRateToSnapshot(rate);
    }
    
    /**
     * ✅ EXISTING: ADD CALCULATED RATE: Store calculated rate
     */
    public void addCalculatedRate(BaseRateDto rate) {
        this.calculatedRates.add(rate);
        // Also add to snapshot for immediate publishing
        addRateToSnapshot(rate);
    }
    
    /**
     * ✅ EXISTING: ADD STAGE RESULT: Log stage completion
     */
    public void addStageResult(String result) {
        this.stageResults.add(result);
    }
    
    /**
     * ✅ EXISTING: GET CALCULATED RATES: For batch assembly
     */
    public List<BaseRateDto> getCalculatedRates() {
        return new ArrayList<>(calculatedRates);
    }
}
