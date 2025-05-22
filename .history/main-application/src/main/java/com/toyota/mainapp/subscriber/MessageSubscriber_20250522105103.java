package com.toyota.mainapp.subscriber;

import com.toyota.mainapp.model.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MessageSubscriber implements Subscriber {
    private static final Logger logger = LogManager.getLogger(MessageSubscriber.class);
    private final String name;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public MessageSubscriber() {
        this.name = "DefaultMessageSubscriber";
    }
    
    public MessageSubscriber(String name) {
        this.name = name;
    }
    
    @Override
    public void onMessage(Message message) {
        if (running.get()) {
            logger.info("Abone {} mesajı aldı: {}", name, message);
            // Implement message processing logic here
            processMessage(message);
        } else {
            logger.warn("Abone {} çalışmıyor, mesaj işlenmedi: {}", name, message);
        }
    }
    
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Abone {} başlatıldı", name);
        } else {
            logger.warn("Abone {} zaten çalışıyor", name);
        }
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Abone {} durduruldu", name);
        } else {
            logger.warn("Abone {} zaten durdurulmuş", name);
        }
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    private void processMessage(Message message) {
        // Add specific message processing logic here
        logger.debug("Mesaj işleniyor: {}", message.getId());
        try {
            // Simulate processing time
            Thread.sleep(100);
            logger.info("Mesaj başarıyla işlendi: {}", message.getId());
        } catch (InterruptedException e) {
            logger.error("Mesaj işleme sırasında hata oluştu: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
