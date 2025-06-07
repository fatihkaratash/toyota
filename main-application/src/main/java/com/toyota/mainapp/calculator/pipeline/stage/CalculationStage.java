package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.calculator.pipeline.StageExecutionException;

/**
 * ✅ CALCULATION STAGE INTERFACE
 * Contract for all pipeline stages
 */
public interface CalculationStage {
    
    /**
     * Execute this stage with given context
     */
    void execute(ExecutionContext context) throws StageExecutionException;
    
    /**
     * Get stage name for logging
     */
    default String getStageName() {
        return this.getClass().getSimpleName();
    }
}
