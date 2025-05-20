package com.toyota.mainapp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Configuration
public class SubscriberConfig {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberConfig.class);

    @Value("${subscribers.config.path:classpath:subscribers.json}")
    private Resource subscribersConfigFile;

    @Bean
    public List<SubscriberDefinition> subscriberDefinitions(ObjectMapper objectMapper) {
        if (subscribersConfigFile == null || !subscribersConfigFile.exists()) {
            logger.error("Abonelik yapılandırma dosyası belirtilmemiş veya bulunamadı: {}",
                         subscribersConfigFile != null ? subscribersConfigFile.getDescription() : "null");
            return Collections.emptyList();
        }
        try (InputStream inputStream = subscribersConfigFile.getInputStream()) {
            List<SubscriberDefinition> definitions = objectMapper.readValue(inputStream, new TypeReference<List<SubscriberDefinition>>() {});
            logger.info("{} abonelik tanımı {} dosyasından yüklendi", definitions.size(), subscribersConfigFile.getFilename());
            
            // Assign default class names if not provided, based on type
            for (SubscriberDefinition def : definitions) {
                if (def.getClassName() == null || def.getClassName().trim().isEmpty()) {
                    if ("tcp".equalsIgnoreCase(def.getType())) {
                        def.setClassName("com.toyota.mainapp.subscriber.impl.TcpRateSubscriber");
                    } else if ("rest".equalsIgnoreCase(def.getType())) {
                        def.setClassName("com.toyota.mainapp.subscriber.impl.RestRateSubscriber");
                    } else {
                        logger.warn("'{}' için bilinmeyen abone türü '{}', className olmadan.", def.getName(), def.getType());
                    }
                }
            }
            return definitions;
        } catch (IOException e) {
            logger.error("Abonelik yapılandırma dosyası yüklenemedi veya ayrıştırılamadı: {}", subscribersConfigFile.getDescription(), e);
            return Collections.emptyList();
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
