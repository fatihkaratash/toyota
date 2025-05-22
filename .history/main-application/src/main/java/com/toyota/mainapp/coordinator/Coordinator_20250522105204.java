package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.model.Message;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.subscriber.Subscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface for the main coordinator that orchestrates the application.
 */
@Service
public class Coordinator {
    private static final Logger logger = LogManager.getLogger(Coordinator.class);
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /**
     * Starts the coordinator.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Koordinatör başlatıldı");
            subscribers.forEach(Subscriber::start);
        } else {
            logger.warn("Koordinatör zaten çalışıyor");
        }
    }
    
    /**
     * Stops the coordinator.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Koordinatör durduruluyor");
            subscribers.forEach(Subscriber::stop);
        } else {
            logger.warn("Koordinatör zaten durdurulmuş");
        }
    }
    
    /**
     * Processes a rate.
     * 
     * @param rate The rate to process
     */
    public void processRate(Rate rate) {
        // Rate processing logic here
    }
    
    /**
     * Registers a subscriber to the coordinator.
     * 
     * @param subscriber The subscriber to register
     */
    public void registerSubscriber(Subscriber subscriber) {
        subscribers.add(subscriber);
        logger.info("Abone kaydedildi: {}", subscriber.getName());
    }
    
    /**
     * Unregisters a subscriber from the coordinator.
     * 
     * @param subscriber The subscriber to unregister
     */
    public void unregisterSubscriber(Subscriber subscriber) {
        subscribers.remove(subscriber);
        logger.info("Abone kaydı silindi: {}", subscriber.getName());
    }
    
    /**
     * Publishes a message to all registered subscribers.
     * 
     * @param message The message to publish
     */
    public void publish(Message message) {
        if (!running.get()) {
            logger.warn("Koordinatör çalışmıyor, mesaj yayınlanamadı: {}", message);
            return;
        }
        
        logger.info("Mesaj yayınlanıyor: {}", message);
        for (Subscriber subscriber : subscribers) {
            try {
                subscriber.onMessage(message);
            } catch (Exception e) {
                logger.error("Abone {} için mesaj işlenirken hata oluştu: {}", subscriber.getName(), e.getMessage());
            }
        }
    }
    
    /**
     * Checks if the coordinator is running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Gets the names of all registered subscribers.
     * 
     * @return A list of subscriber names
     */
    public List<String> getRegisteredSubscriberNames() {
        List<String> names = new ArrayList<>();
        for (Subscriber subscriber : subscribers) {
            names.add(subscriber.getName());
        }
        return names;
    }
}
