package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;

/**
 * ✅ CALCULATION STAGE INTERFACE
 * Contract for all pipeline stages
 */
public interface CalculationStage {
    
    /**
     * Execute this stage with given context
     */
    void execute(ExecutionContext context);
    
    /**
     * Get stage name for logging
     */
    default String getStageName() {
        return this.getClass().getSimpleName();
    }
}
