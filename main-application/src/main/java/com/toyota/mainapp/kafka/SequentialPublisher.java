package com.toyota.mainapp.kafka;

import com.toyota.mainapp.dto.model.BaseRateDto;

import java.util.List;

/**
 * ✅ ENHANCED: Sequential publisher interface for immediate snapshot pipeline
 * Supports immediate snapshot publishing with pipeline grouping
 */
public interface SequentialPublisher {
    
    /**
     * ✅ NEW: Publish immediate snapshot as separate messages with same pipelineId
     * CRITICAL: Each rate published as individual message for consumer reassembly
     */
    void publishImmediateSnapshot(List<String> rateStrings, String pipelineId);
    
    /**
     * ✅ LEGACY: Publish individual rate (determines type automatically)
     */
    void publishRate(BaseRateDto rate);
    
    /**
     * ✅ LEGACY: Publish raw rate to individual JSON topic
     */
    void publishRawRate(BaseRateDto rawRate);
    
    /**
     * ✅ LEGACY: Publish calculated rate to individual JSON topic  
     */
    void publishCalculatedRate(BaseRateDto calculatedRate);
    
    /**
     * ✅ LEGACY: Publish simple rates batch (for backward compatibility)
     */
    void publishSimpleRatesBatch(List<BaseRateDto> rates);
    
    /**
     * ✅ LEGACY: Publish string batch (pipe-delimited format)
     */
    void publishStringBatch(String batchString);
}