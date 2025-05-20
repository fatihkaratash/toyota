package com.toyota.mainapp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.util.LoggingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SubscriberConfig {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberConfig.class);

    @Value("${subscribers.config.path:classpath:subscribers.json}")
    private Resource subscribersConfigFile;
    
    @Value("${subscribers.enabled:true}")
    private boolean subscribersEnabled;

    @Bean
    public List<SubscriberDefinition> subscriberDefinitions(ObjectMapper objectMapper) {
        if (!subscribersEnabled) {
            logger.warn("Aboneler yapılandırma ile devre dışı bırakıldı. Hiçbir abone başlatılmayacak.");
            return Collections.emptyList();
        }
        
        if (subscribersConfigFile == null || !subscribersConfigFile.exists()) {
            LoggingHelper.logWarning(logger, "SubscriberConfig", 
                    "Abonelik yapılandırma dosyası belirtilmemiş veya bulunamadı: " +
                    (subscribersConfigFile != null ? subscribersConfigFile.getDescription() : "null"));
            return Collections.emptyList();
        }
        
        try (InputStream inputStream = subscribersConfigFile.getInputStream()) {
            List<SubscriberDefinition> definitions = objectMapper.readValue(
                    inputStream, new TypeReference<List<SubscriberDefinition>>() {});
            
            // Validate and assign default values to subscriber definitions
            List<SubscriberDefinition> validDefinitions = validateAndEnrichDefinitions(definitions);
            
            LoggingHelper.logInitialized(logger, "SubscriberConfig");
            logger.info("{} abone tanımı {} dosyasından yüklendi", 
                    validDefinitions.size(), subscribersConfigFile.getFilename());
            
            return validDefinitions;
        } catch (IOException e) {
            LoggingHelper.logError(logger, "SubscriberConfig", 
                    "Abonelik yapılandırma dosyası yüklenemedi veya ayrıştırılamadı: " + 
                    subscribersConfigFile.getDescription(), e);
            return Collections.emptyList();
        }
    }
    
    private List<SubscriberDefinition> validateAndEnrichDefinitions(List<SubscriberDefinition> definitions) {
        return definitions.stream()
                .filter(this::validateDefinition)
                .map(this::enrichDefinition)
                .collect(Collectors.toList());
    }
    
    private boolean validateDefinition(SubscriberDefinition def) {
        // Basic validation of required fields
        if (def.getName() == null || def.getName().trim().isEmpty()) {
            logger.error("Geçersiz abone tanımı: 'name' alanı gerekli ancak boş");
            return false;
        }
        
        if (def.getType() == null || def.getType().trim().isEmpty()) {
            logger.error("Geçersiz abone tanımı: '{}' için 'type' alanı gerekli ancak boş", def.getName());
            return false;
        }
        
        if (def.getSubscribedSymbols() == null || def.getSubscribedSymbols().isEmpty()) {
            logger.error("Geçersiz abone tanımı: '{}' için 'subscribedSymbols' alanı gerekli ancak boş", def.getName());
            return false;
        }
        
        // Type-specific validation
        if ("tcp".equalsIgnoreCase(def.getType())) {
            if (def.getHost() == null || def.getHost().trim().isEmpty()) {
                logger.error("Geçersiz TCP abone tanımı: '{}' için 'host' alanı gerekli", def.getName());
                return false;
            }
            if (def.getPort() == null) {
                logger.error("Geçersiz TCP abone tanımı: '{}' için 'port' alanı gerekli", def.getName());
                return false;
            }
        } else if ("rest".equalsIgnoreCase(def.getType())) {
            if (def.getUrl() == null || def.getUrl().trim().isEmpty()) {
                logger.error("Geçersiz REST abone tanımı: '{}' için 'url' alanı gerekli", def.getName());
                return false;
            }
        } else {
            logger.warn("Tanınmayan abone türü: '{}' için '{}'", def.getName(), def.getType());
            // Still return true, we'll try to handle it with className
        }
        
        return true;
    }
    
    private SubscriberDefinition enrichDefinition(SubscriberDefinition def) {
        // Assign default class names based on type if not provided
        if (def.getClassName() == null || def.getClassName().trim().isEmpty()) {
            if ("tcp".equalsIgnoreCase(def.getType())) {
                def.setClassName("com.toyota.mainapp.subscriber.impl.TcpRateSubscriber");
                logger.info("'{}' için varsayılan TCP abone sınıfı atandı", def.getName());
            } else if ("rest".equalsIgnoreCase(def.getType())) {
                def.setClassName("com.toyota.mainapp.subscriber.impl.RestRateSubscriber");
                logger.info("'{}' için varsayılan REST abone sınıfı atandı", def.getName());
            } else {
                logger.warn("'{}' için bilinmeyen abone türü '{}', className olmadan.", def.getName(), def.getType());
            }
        }
        
        // Ensure additionalProperties map exists
        if (def.getAdditionalProperties() == null) {
            def.setAdditionalProperties(new HashMap<>());
        }
        
        // Type-specific default property enrichment
        if ("tcp".equalsIgnoreCase(def.getType())) {
            Map<String, Object> props = def.getAdditionalProperties();
            if (!props.containsKey("connectionTimeoutMs")) {
                props.put("connectionTimeoutMs", 5000); // 5 seconds default
            }
            if (!props.containsKey("readTimeoutMs")) {
                props.put("readTimeoutMs", 10000); // 10 seconds default
            }
        } else if ("rest".equalsIgnoreCase(def.getType())) {
            // Assign default poll interval if not provided
            if (def.getPollIntervalMs() == null) {
                def.setPollIntervalMs(10000L); // 10 seconds default
                logger.info("'{}' için varsayılan pollIntervalMs=10000 atandı", def.getName());
            }
            
            Map<String, Object> props = def.getAdditionalProperties();
            if (!props.containsKey("readTimeoutMs")) {
                props.put("readTimeoutMs", 5000); // 5 seconds default
            }
            if (!props.containsKey("connectTimeoutMs")) {
                props.put("connectTimeoutMs", 5000); // 5 seconds default
            }
        }
        
        return def;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
