package com.toyota.mainapp.subscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the Subscriber interface that connects to a message broker
 * and processes received messages.
 */
@Component
public class MessageSubscriber implements Subscriber {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageSubscriber.class);
    
    private final MessageHandler messageHandler;
    private final Set<String> subscribedTopics = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    
    @Autowired
    public MessageSubscriber(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }
    
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Abone başlatılıyor...");
            executorService = Executors.newFixedThreadPool(5);
            
            // Subscribe to all topics
            synchronized (subscribedTopics) {
                subscribedTopics.forEach(this::setupSubscription);
            }
            
            logger.info("Abone başlatıldı ve {} konulara bağlandı", subscribedTopics.size());
        } else {
            logger.warn("Abone zaten çalışıyor!");
        }
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Abone durduruluyor...");
            
            // Unsubscribe from all topics
            synchronized (subscribedTopics) {
                subscribedTopics.clear();
            }
            
            // Shutdown executor service
            if (executorService != null) {
                executorService.shutdown();
            }
            
            logger.info("Abone durduruldu");
        } else {
            logger.warn("Abone zaten durmuş durumda!");
        }
    }
    
    @Override
    public void subscribe(String topic) {
        synchronized (subscribedTopics) {
            if (subscribedTopics.add(topic)) {
                logger.info("'{}' konusuna abone olunuyor", topic);
                if (running.get()) {
                    setupSubscription(topic);
                }
            }
        }
    }
    
    @Override
    public void unsubscribe(String topic) {
        synchronized (subscribedTopics) {
            if (subscribedTopics.remove(topic)) {
                logger.info("'{}' konusundan abonelik kaldırılıyor", topic);
                // Additional logic to remove active subscriptions would go here
            }
        }
    }
    
    private void setupSubscription(String topic) {
        // This would typically connect to a message broker and set up a subscription
        // For example, using a Kafka consumer, MQTT client, etc.
        // For demonstration purposes, we'll simulate message receiving with a thread
        
        if (running.get() && executorService != null) {
            executorService.submit(() -> {
                logger.info("'{}' konusuna abone olundu, mesajlar dinleniyor", topic);
                
                // Simulate receiving messages (in a real app, this would be callback-based)
                while (running.get() && subscribedTopics.contains(topic)) {
                    try {
                        Thread.sleep(5000);
                        // In a real app, you would receive messages from the broker here
                        // For simulation, we'll create a test message
                        if (running.get() && subscribedTopics.contains(topic)) {
                            String message = "Test mesajı " + System.currentTimeMillis();
                            messageHandler.handleMessage(topic, message);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception e) {
                        logger.error("Mesaj işlenirken hata oluştu: {}", e.getMessage(), e);
                    }
                }
            });
        }
    }
}
