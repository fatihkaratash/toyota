package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;

/**
 * Toyota Financial Data Platform - Calculation Stage Interface
 * 
 * Contract defining the execution behavior for all pipeline stages within
 * the rate calculation pipeline. Provides standardized stage execution
 * and naming conventions for the financial data processing framework.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
public interface CalculationStage {

    void execute(ExecutionContext context);

    default String getStageName() {
        return this.getClass().getSimpleName();
    }
}
