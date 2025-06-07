package com.toyota.mainapp.calculator.pipeline;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;

/**
 * Result of stage execution
 */
@Data
@Builder
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
        return StageResult.builder()
                .stageName(stageName)
                .success(true)
                .message(message)
                .itemsProcessed(itemsProcessed)
                .startTime(Instant.now())
                .endTime(Instant.now())
                .build();
    }
    
    public static StageResult failure(String stageName, String message, Throwable error) {
        return StageResult.builder()
                .stageName(stageName)
                .success(false)
                .message(message)
                .error(error)
                .startTime(Instant.now())
                .endTime(Instant.now())
                .itemsProcessed(0)
                .build();
    }
}
