package com.toyota.mainapp.subscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default implementation of MessageHandler that logs received messages.
 */
@Component
public class DefaultMessageHandler implements MessageHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultMessageHandler.class);
    
    @Override
    public void handleMessage(String topic, String message) {
        logger.info("Mesaj alındı - Konu: {}, Mesaj: {}", topic, message);
        // Message processing logic goes here
    }
}
