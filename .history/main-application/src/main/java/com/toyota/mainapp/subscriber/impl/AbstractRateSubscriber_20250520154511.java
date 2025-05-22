package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.logging.LoggingHelper;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class for rate subscribers that implements common functionality.
 * This reduces code duplication between different subscriber implementations.
 */
public abstract class AbstractRateSubscriber implements PlatformSubscriber {
    
    protected final Logger logger;
    protected SubscriberDefinition definition;
    protected PlatformCallback callback;
    protected final AtomicBoolean active = new AtomicBoolean(false);
    
    protected AbstractRateSubscriber() {
        this.logger = LoggerFactory.getLogger(this.getClass());
    }
    
    @Override
    public void initialize(SubscriberDefinition definition, PlatformCallback callback) {
        this.definition = definition;
        this.callback = callback;
        initializeResources();
        logger.info("{} abone türü {} semboller için başlatıldı: {}", 
                getSubscriberType(), definition.getName(), definition.getSubscribedSymbols());
    }
    
    @Override
    public void startSubscription() {
        if (active.compareAndSet(false, true)) {
            LoggingHelper.logStartup(logger, definition.getName(), LoggingHelper.COMPONENT_SUBSCRIBER);
            startSubscriptionInternal();
            callback.onStatusChange(definition.getName(), "Abonelik başlatıldı");
        } else {
            LoggingHelper.logWarning(logger, definition.getName(), LoggingHelper.COMPONENT_SUBSCRIBER, 
                    "Zaten aktif, yeniden başlatılamaz");
        }
    }
    
    @Override
    public void stopSubscription() {
        if (active.compareAndSet(true, false)) {
            LoggingHelper.logShutdown(logger, definition.getName(), LoggingHelper.COMPONENT_SUBSCRIBER);
            stopSubscriptionInternal();
            logger.info("{} {} durduruldu", getSubscriberType(), definition.getName());
            callback.onStatusChange(definition.getName(), "Abonelik durduruldu");
        } else {
            logger.warn("{} {} zaten durdurulmuş veya başlatılmamış", getSubscriberType(), definition.getName());
        }
    }
    
    @Override
    public String getPlatformName() {
        return definition != null ? definition.getName() : "Başlatılmamış " + getSubscriberType();
    }
    
    @Override
    public List<String> getSubscribedSymbols() {
        return definition != null ? definition.getSubscribedSymbols() : List.of();
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
    
    /**
     * Gets a property value from the subscriber definition's additional properties map.
     * 
     * @param <T> The type of the property value
     * @param key The property key
     * @param defaultValue The default value if property is not found or has wrong type
     * @return The property value or default value
     */
    @SuppressWarnings("unchecked")
    protected <T> T getProperty(String key, T defaultValue) {
        if (definition == null || definition.getAdditionalProperties() == null) {
            return defaultValue;
        }
        
        Object value = definition.getAdditionalProperties().get(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            if (defaultValue != null && defaultValue.getClass().isInstance(value)) {
                return (T) value;
            } else if (defaultValue instanceof Number && value instanceof Number) {
                // Handle numeric conversion
                if (defaultValue instanceof Integer) {
                    return (T) Integer.valueOf(((Number) value).intValue());
                } else if (defaultValue instanceof Long) {
                    return (T) Long.valueOf(((Number) value).longValue());
                } else if (defaultValue instanceof Double) {
                    return (T) Double.valueOf(((Number) value).doubleValue());
                }
            }
        } catch (Exception e) {
            logger.warn("{} için {} özelliği tip dönüşümü hatası: {}", 
                    definition.getName(), key, e.getMessage());
        }
        
        return defaultValue;
    }
    
    /**
     * Finds a property in the additional properties map, wrapped in an Optional.
     * 
     * @param <T> The expected type of the property
     * @param key The property key
     * @param clazz The expected class of the property
     * @return An Optional containing the property value, or empty if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    protected <T> Optional<T> findProperty(String key, Class<T> clazz) {
        if (definition == null || definition.getAdditionalProperties() == null) {
            return Optional.empty();
        }
        
        Object value = definition.getAdditionalProperties().get(key);
        if (value == null) {
            return Optional.empty();
        }
        
        if (clazz.isInstance(value)) {
            return Optional.of((T) value);
        }
        
        return Optional.empty();
    }
    
    /**
     * Gets a property from the map as a Map.
     * 
     * @param key The property key
     * @return The map property or empty map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, String> getMapProperty(String key) {
        if (definition == null || definition.getAdditionalProperties() == null) {
            return Map.of();
        }
        
        Object value = definition.getAdditionalProperties().get(key);
        if (value instanceof Map) {
            return (Map<String, String>) value;
        }
        
        return Map.of();
    }
    
    /**
     * Gets the type name of the subscriber for logging purposes.
     * 
     * @return The subscriber type name
     */
    protected abstract String getSubscriberType();
    
    /**
     * Initializes resources specific to the subscriber implementation.
     * Called during the initialize method.
     */
    protected abstract void initializeResources();
    
    /**
     * Starts the actual subscription flow.
     * Implementations should perform the actual subscription logic here.
     */
    protected abstract void startSubscriptionInternal();
    
    /**
     * Stops the actual subscription flow.
     * Implementations should clean up resources here.
     */
    protected abstract void stopSubscriptionInternal();
}
