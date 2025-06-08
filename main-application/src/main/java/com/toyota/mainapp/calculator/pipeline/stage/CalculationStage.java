package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;

/**
 *  CALCULATION STAGE INTERFACE
 * Contract for all pipeline stages
 */
public interface CalculationStage {

    void execute(ExecutionContext context);

    default String getStageName() {
        return this.getClass().getSimpleName();
    }
}
