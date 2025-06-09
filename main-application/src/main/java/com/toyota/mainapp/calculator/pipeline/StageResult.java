package com.toyota.mainapp.calculator.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;

/**
 * Toyota Financial Data Platform - Pipeline Stage Result
 * 
 * Encapsulates execution results from individual pipeline stages including
 * success status, performance metrics, and error details. Enables comprehensive
 * monitoring and debugging of the rate calculation pipeline.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Data
@Builder
@AllArgsConstructor
public class StageResult {
    
    private final String stageName;
    private final boolean success;
    private final String message;
    private final Throwable error;
    private final Instant startTime;
    private final Instant endTime;
    private final int itemsProcessed;
    
    public Duration getExecutionDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return Duration.ZERO;
    }
    
    public static StageResult success(String stageName, int itemsProcessed, String message) {
        return new StageResult(stageName, true, message, null, Instant.now(), Instant.now(), itemsProcessed);
    }
    
    public static StageResult failure(String stageName, String message, Throwable error) {
        return new StageResult(stageName, false, message, error, Instant.now(), Instant.now(), 0);
    }
}
