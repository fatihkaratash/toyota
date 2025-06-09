package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.RealTimeBatchProcessor;
import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.ProviderRateDto;
import com.toyota.mainapp.exception.AggregatedRateValidationException;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.mapper.RateMapper;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.dynamic.DynamicSubscriberLoader;
import com.toyota.mainapp.subscriber.impl.RestRateSubscriber;
import com.toyota.mainapp.subscriber.impl.TcpRateSubscriber;
import com.toyota.mainapp.util.SymbolUtils;
import com.toyota.mainapp.validation.RateValidatorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.toyota.mainapp.config.ApplicationProperties;

/**
 * Toyota Financial Data Platform - Main Coordinator Service
 * 
 * Central orchestration service managing subscriber lifecycle, rate processing
 * pipeline, and system health monitoring. Coordinates data flow from multiple
 * providers through validation, caching, calculation, and Kafka publishing.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MainCoordinatorService implements PlatformCallback {

    private final DynamicSubscriberLoader dynamicSubscriberLoader;
    @Qualifier("subscriberTaskExecutor")
    private final TaskExecutor subscriberTaskExecutor;
    @Qualifier("pipelineTaskExecutor")
    private final TaskExecutor pipelineTaskExecutor;
    private final RateMapper rateMapper;
    private final RateValidatorService rateValidatorService;
    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;
    private final RealTimeBatchProcessor realTimeBatchProcessor;
    private final ApplicationProperties appProperties;

    private final Map<String, PlatformSubscriber> activeSubscribers = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeAndStartSubscribers() {
        log.info("MainCoordinatorService initializing...");
        
        int startupDelaySeconds = getEnvInt("STARTUP_DELAY_SECONDS", 10);
        try {
            Thread.sleep(startupDelaySeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            Collection<PlatformSubscriber> subscribers = dynamicSubscriberLoader.loadSubscribers(
                appProperties.getSubscribersConfigPath(), this);
            
            if (subscribers.isEmpty()) {
                log.warn("No subscribers loaded");
                return;
            }

            log.info("Loaded {} subscribers", subscribers.size());
            
            subscribers.forEach(subscriber -> {
                activeSubscribers.put(subscriber.getProviderName(), subscriber);
                subscriberTaskExecutor.execute(() -> startSubscriber(subscriber));
            });
            
        } catch (Exception e) {
            log.error("Error loading subscribers", e);
        }
    }
    
    private void startSubscriber(PlatformSubscriber subscriber) {
        String providerName = subscriber.getProviderName();
        try {
            subscriber.connect();
            if (subscriber.isConnected()) {
                subscriber.startMainLoop();
            }
        } catch (Exception e) {
            log.error("Subscriber startup error: {}", providerName, e);
        }
    }

    @Override
    public void onRateAvailable(String providerName, ProviderRateDto providerRate) {
        pipelineTaskExecutor.execute(() -> {
            try {
                if (providerRate.getProviderName() == null) {
                    providerRate.setProviderName(providerName);
                }

                BaseRateDto baseRate = rateMapper.toBaseRateDto(providerRate);
                String normalizedSymbol = SymbolUtils.normalizeSymbol(baseRate.getSymbol());
                
                if (!SymbolUtils.isValidSymbol(normalizedSymbol)) return;
                
                baseRate.setSymbol(normalizedSymbol);
                rateValidatorService.validate(baseRate);
                baseRate.setValidatedAt(System.currentTimeMillis());

                rateCacheService.cacheRawRate(baseRate);
                kafkaPublishingService.publishRawRate(baseRate);
                realTimeBatchProcessor.processNewRate(baseRate);

            } catch (AggregatedRateValidationException e) {
                log.warn("Rate validation failed from {}: {}", providerName, e.getErrors());
            } catch (Exception e) {
                log.error("Pipeline error from {}: {}", providerName, e.getMessage());
            }
        });
    }

    @Override
    public void onRateUpdate(String providerName, ProviderRateDto rateUpdate) {
        onRateAvailable(providerName, rateUpdate);
    }

    @Override
    public void onProviderConnectionStatus(String providerName, boolean isConnected, String statusMessage) {
        log.info("Provider {} {}", providerName, isConnected ? "connected" : "disconnected");
    }

    @Override
    public void onRateStatus(String providerName, BaseRateDto statusRate) {
        kafkaPublishingService.publishRate(statusRate);
        if (statusRate.getStatus() == BaseRateDto.RateStatusEnum.ERROR) {
            log.warn("Error status from provider {}: {}", providerName, statusRate.getSymbol());
        }
    }

    @Override
    public void onProviderError(String providerName, String errorMessage, Throwable throwable) {
        log.error("Provider error {}: {}", providerName, errorMessage);
    }

    public void stopSubscriber(String providerName) {
        PlatformSubscriber subscriber = activeSubscribers.remove(providerName);
        if (subscriber != null) {
            try {
                subscriber.stopMainLoop();
                subscriber.disconnect();
            } catch (Exception e) {
                log.error("Error stopping subscriber: {}", providerName, e);
            }
        }
    }

    @PreDestroy
    public void shutdownCoordinator() {
        log.info("Shutting down coordinator");
        activeSubscribers.keySet().forEach(this::stopSubscriber);
    }

    public Map<String, Object> getActiveSubscribersStatus() {
        Map<String, Object> status = new HashMap<>();
        Map<String, Object> subscribers = new HashMap<>();
        
        activeSubscribers.forEach((name, subscriber) -> {
            Map<String, Object> subscriberInfo = new HashMap<>();
            subscriberInfo.put("connected", subscriber.isConnected());
            subscriberInfo.put("type", subscriber.getClass().getSimpleName());
            subscribers.put(name, subscriberInfo);
        });
        
        status.put("activeCount", activeSubscribers.size());
        status.put("subscribers", subscribers);
        status.put("timestamp", System.currentTimeMillis());
        
        return status;
    }

    public void restartSubscriber(String providerName) {
        stopSubscriber(providerName);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        reloadSubscribersConfiguration();
    }

    public Map<String, Object> getSystemHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        int totalSubscribers = activeSubscribers.size();
        long connectedCount = activeSubscribers.values().stream()
            .mapToLong(subscriber -> subscriber.isConnected() ? 1 : 0)
            .sum();
        
        health.put("totalSubscribers", totalSubscribers);
        health.put("connectedSubscribers", connectedCount);
        health.put("healthScore", totalSubscribers > 0 ? (connectedCount * 100.0 / totalSubscribers) : 0);
        health.put("status", connectedCount == totalSubscribers ? "HEALTHY" : "DEGRADED");
        health.put("timestamp", System.currentTimeMillis());
        
        return health;
    }

    public void reloadSubscribersConfiguration() {
        activeSubscribers.keySet().forEach(this::stopSubscriber);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        initializeAndStartSubscribers();
    }

    public boolean addSymbolSubscription(String providerName, String symbol) {
        PlatformSubscriber subscriber = activeSubscribers.get(providerName);
        if (subscriber == null) return false;
        
        if (subscriber instanceof TcpRateSubscriber) {
            return ((TcpRateSubscriber) subscriber).addSymbolSubscription(symbol.toUpperCase());
        } else if (subscriber instanceof RestRateSubscriber) {
            return ((RestRateSubscriber) subscriber).addSymbolToPolling(symbol.toUpperCase());
        }
        
        return false;
    }

    public boolean removeSymbolSubscription(String providerName, String symbol) {
        PlatformSubscriber subscriber = activeSubscribers.get(providerName);
        if (subscriber == null) return false;
        
        if (subscriber instanceof TcpRateSubscriber) {
            return ((TcpRateSubscriber) subscriber).removeSymbolSubscription(symbol.toUpperCase());
        } else if (subscriber instanceof RestRateSubscriber) {
            return ((RestRateSubscriber) subscriber).removeSymbolFromPolling(symbol.toUpperCase());
        }
        
        return false;
    }

    public Map<String, Object> getProviderSubscriptions(String providerName) {
        Map<String, Object> result = new HashMap<>();
        PlatformSubscriber subscriber = activeSubscribers.get(providerName);
        
        if (subscriber == null) {
            result.put("error", "Provider not found");
            return result;
        }
        
        result.put("providerName", providerName);
        result.put("type", subscriber.getClass().getSimpleName());
        result.put("connected", subscriber.isConnected());
        
        if (subscriber instanceof TcpRateSubscriber) {
            result.put("activeSubscriptions", ((TcpRateSubscriber) subscriber).getActiveSubscriptions());
            result.put("protocol", "TCP");
        } else if (subscriber instanceof RestRateSubscriber) {
            result.put("pollingSymbols", ((RestRateSubscriber) subscriber).getPollingSymbols());
            result.put("protocol", "REST");
        }
        
        return result;
    }

    public void addNewProvider(SubscriberConfigDto config) throws Exception {
        if (activeSubscribers.containsKey(config.getName())) {
            throw new IllegalArgumentException("Provider already exists: " + config.getName());
        }
        
        PlatformSubscriber subscriber = dynamicSubscriberLoader.createSubscriberInstance(config, this);
        activeSubscribers.put(config.getName(), subscriber);
        subscriberTaskExecutor.execute(() -> startSubscriber(subscriber));
        
        log.info("New provider added: {}", config.getName());
    }

    private int getEnvInt(String envName, int defaultValue) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }
}
