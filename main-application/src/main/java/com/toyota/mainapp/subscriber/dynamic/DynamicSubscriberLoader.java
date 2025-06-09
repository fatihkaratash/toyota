package com.toyota.mainapp.subscriber.dynamic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;
import com.toyota.mainapp.exception.SubscriberInitializationException;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.impl.RestRateSubscriber;
import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Toyota Financial Data Platform - Dynamic Subscriber Loader
 * 
 * Configuration-driven subscriber factory that dynamically instantiates and configures
 * rate data subscribers from JSON configuration files. Supports multiple subscriber types
 * including REST and TCP-based implementations with resilience patterns.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Component
public class DynamicSubscriberLoader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final WebClient.Builder webClientBuilder;
    private final TaskExecutor subscriberTaskExecutor;

    /**
     * ✅ UPDATED: Constructor with @Qualifier for new executor bean names
     */
    public DynamicSubscriberLoader(ObjectMapper objectMapper,
                                   ResourceLoader resourceLoader,
                                   @Autowired(required = false) WebClient.Builder webClientBuilder,
                                   @Qualifier("subscriberTaskExecutor") TaskExecutor subscriberTaskExecutor) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.webClientBuilder = webClientBuilder;
        this.subscriberTaskExecutor = subscriberTaskExecutor;
    }

    /**
     * Aboneleri yapılandırma dosyasından yükle
     */
    @CircuitBreaker(name = "subscriberLoader")
    @Retry(name = "loaderRetry")
    public List<PlatformSubscriber> loadSubscribers(String configPath, PlatformCallback callback) {
        List<PlatformSubscriber> subscribers = new ArrayList<>();
        
        try {
            List<SubscriberConfigDto> configs = readConfiguration(configPath);
            
            if (configs.isEmpty()) {
                log.warn("No subscribers found in config: {}", configPath);
                return subscribers;
            }
            
            for (SubscriberConfigDto config : configs) {
                if (!config.isEnabled()) continue;
                
                try {
                    PlatformSubscriber subscriber = createSubscriberInstance(config, callback);
                    subscribers.add(subscriber);
                } catch (Exception e) {
                    log.error("Failed to create subscriber: {} - {}", config.getName(), e.getMessage());
                }
            }
            
            log.info("Loaded {} subscribers", subscribers.size());
            
        } catch (Exception e) {
            log.error("Error loading subscribers from {}: {}", configPath, e.getMessage());
        }
        
        return subscribers;
    }

    /**
     * Yapılandırma dosyasını oku
     */
    private List<SubscriberConfigDto> readConfiguration(String configPath) throws IOException {
        Resource resource = resourceLoader.getResource(configPath);
        
        if (!resource.exists()) {
            log.error("Configuration file not found: {}", configPath);
            return Collections.emptyList();
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            ObjectMapper localMapper = objectMapper.copy();
            localMapper.deactivateDefaultTyping();
            
            String jsonContent = new String(inputStream.readAllBytes());
            return localMapper.readValue(jsonContent, new TypeReference<List<SubscriberConfigDto>>() {});
            
        } catch (IOException e) {
            log.error("Failed to read configuration file: {}", configPath);
            throw e;
        }
    }

    /**
     * Abone örneği oluştur
     * ✅ ENHANCED: Better error handling and validation for immediate pipeline requirements
     */
    public PlatformSubscriber createSubscriberInstance(SubscriberConfigDto config, PlatformCallback callback) 
            throws ReflectiveOperationException, SubscriberInitializationException {
        
        String implementationClass = config.getImplementationClass();
        if (implementationClass == null || implementationClass.isEmpty()) {
            throw new IllegalArgumentException("Implementation class not specified: " + config.getName());
        }
        
        PlatformSubscriber subscriber;
        
        if (implementationClass.contains("RestRateSubscriber")) {
            subscriber = new RestRateSubscriber(webClientBuilder, objectMapper, subscriberTaskExecutor);
        } else if (implementationClass.contains("TcpRateSubscriber")) {
            subscriber = new com.toyota.mainapp.subscriber.impl.TcpRateSubscriber();
        } else {
            Class<?> subscriberClass = Class.forName(implementationClass);
            if (!PlatformSubscriber.class.isAssignableFrom(subscriberClass)) {
                throw new IllegalArgumentException("Class must implement PlatformSubscriber: " + subscriberClass.getName());
            }
            subscriber = (PlatformSubscriber) subscriberClass.getDeclaredConstructor().newInstance();
        }
        
        subscriber.init(config, callback);
        return subscriber;
    }
}
