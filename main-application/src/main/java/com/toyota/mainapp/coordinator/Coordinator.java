package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.model.Rate;

/**
 * Central coordinator interface that orchestrates the data flow in the main application.
 * Responsible for initializing subscribers, handling rate validation, caching, calculation,
 * and publication to Kafka.
 */
public interface Coordinator {
    
    /**
     * Starts the coordinator, initializing all configured components and subscribers.
     */
    void start();
    
    /**
     * Stops the coordinator and all managed components gracefully.
     */
    void stop();
    
    /**
     * Processes a new raw rate that has been received from a subscriber.
     * This method orchestrates validation, caching, calculation, and publication.
     * 
     * @param rate The raw rate received from a provider
     */
    void processRate(Rate rate);
    
    /**
     * @return true if the coordinator is currently running, false otherwise
     */
    boolean isRunning();
}
