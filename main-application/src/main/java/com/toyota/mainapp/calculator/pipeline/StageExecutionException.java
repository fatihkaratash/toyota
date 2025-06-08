package com.toyota.mainapp.calculator.pipeline;

/**
 * ✅ STAGE EXCEPTION: Custom exception for stage execution failures (if needed for compatibility)
 */
public class StageExecutionException extends RuntimeException {
    
    public StageExecutionException(String message) {
        super(message);
    }
    
    public StageExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
