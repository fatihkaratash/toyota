package com.toyota.mainapp.calculator.pipeline;

/**
 * ✅ STAGE EXECUTION EXCEPTION
 * Thrown when a pipeline stage fails
 */
public class StageExecutionException extends Exception {
    
    public StageExecutionException(String message) {
        super(message);
    }
    
    public StageExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
