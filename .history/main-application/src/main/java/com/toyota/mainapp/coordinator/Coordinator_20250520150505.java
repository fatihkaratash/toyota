package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.model.Rate;

/**
 * Interface for the main coordinator that orchestrates the application.
 */
public interface Coordinator {
    
    /**
     * Starts the coordinator.
     */
    void start();
    
    /**
     * Stops the coordinator.
     */
    void stop();
    
    /**
     * Processes a rate.
     * 
     * @param rate The rate to process
     */
    void processRate(Rate rate);
    
    /**
     * Checks if the coordinator is running.
     * 
     * @return true if running, false otherwise
     */
    boolean isRunning();
}
