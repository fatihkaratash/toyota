package com.toyota.mainapp.calculator.pipeline;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import lombok.Data;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * Pipeline execution context - carries state through calculation stages
 */
@Data
@Builder
@Slf4j
public class ExecutionContext {
    
    /**
     * Original triggering rate that started this pipeline
     */
    private BaseRateDto triggeringRate;
    
    /**
     * Unique pipeline execution ID for tracking
     */
    private String pipelineId;
    
    /**
     * Pipeline start time for latency measurement
     */
    private long startTime;
    
    // Configuration
    private final List<CalculationRuleDto> availableRules;
    
    /**
     * Raw rates collected during pipeline execution
     */
    @Builder.Default
    private Map<String, BaseRateDto> collectedRawRates = new ConcurrentHashMap<>();
    
    /**
     * Calculated rates produced during pipeline execution
     */
    @Builder.Default
    private List<BaseRateDto> calculatedRates = new ArrayList<>();
    
    /**
     * Stage execution results for monitoring
     */
    @Builder.Default
    private List<String> stageResults = new ArrayList<>();
    
    /**
     * Error tracking
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    
    /**
     * Add calculated rate to context
     */
    public void addCalculatedRate(BaseRateDto rate) {
        if (rate != null) {
            calculatedRates.add(rate);
        }
    }
    
    /**
     * Add raw rate to context
     */
    public void addRawRate(String key, BaseRateDto rate) {
        if (key != null && rate != null) {
            collectedRawRates.put(key, rate);
        }
    }
    
    /**
     * Add stage result
     */
    public void addStageResult(String result) {
        if (result != null) {
            stageResults.add(result);
        }
    }
    
    /**
     * Add error
     */
    public void addError(String error) {
        if (error != null) {
            errors.add(error);
        }
    }
    
    /**
     * Check if pipeline has errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Get execution duration
     */
    public long getExecutionDuration() {
        return System.currentTimeMillis() - startTime;
    }
}
