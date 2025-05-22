package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.subscriber.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Interface for the main coordinator that orchestrates the application.
 */
@Component
public class Coordinator {
    
    private static final Logger logger = LoggerFactory.getLogger(Coordinator.class);
    
    private final Subscriber subscriber;
    
    @Autowired
    public Coordinator(Subscriber subscriber) {
        this.subscriber = subscriber;
    }
    
    /**
     * Starts the coordinator.
     */
    public void start() {
        logger.info("Koordinatör başlatılıyor...");
        
        // Start subscriber and subscribe to required topics
        subscriber.start();
        subscriber.subscribe("toyota/sensors/temperature");
        subscriber.subscribe("toyota/sensors/pressure");
        subscriber.subscribe("toyota/vehicle/status");
        
        logger.info("Koordinatör başlatıldı.");
    }
    
    /**
     * Stops the coordinator.
     */
    public void stop() {
        logger.info("Koordinatör durduruluyor...");
        
        // Stop subscriber
        subscriber.stop();
        
        logger.info("Koordinatör durduruldu.");
    }
    
    /**
     * Processes a rate.
     * 
     * @param rate The rate to process
     */
    public void processRate(Rate rate) {
        // Implementation for processing rate
    }
    
    /**
     * Checks if the coordinator is running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        // Implementation to check if coordinator is running
        return false;
    }
}
