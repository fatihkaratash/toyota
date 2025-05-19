package com.toyota.mainapp.subscriber.dynamic;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SubscriberRegistryImpl implements SubscriberRegistry {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberRegistryImpl.class);

    private final Map<String, PlatformSubscriber> activeSubscribers = new ConcurrentHashMap<>();
    private final List<SubscriberDefinition> subscriberDefinitions;
    private final DynamicClassLoader dynamicClassLoader;
    // The PlatformCallback will be implemented by the MainCoordinator and injected here.
    // For now, this might be null or a placeholder until MainCoordinator is implemented.
    private final PlatformCallback platformCallback;


    @Autowired
    public SubscriberRegistryImpl(List<SubscriberDefinition> subscriberDefinitions,
                                  DynamicClassLoader dynamicClassLoader,
                                  PlatformCallback platformCallback) { // MainCoordinator will provide this
        this.subscriberDefinitions = subscriberDefinitions;
        this.dynamicClassLoader = dynamicClassLoader;
        this.platformCallback = platformCallback; // This needs to be a concrete implementation
    }
    
    // Constructor for cases where PlatformCallback might not be immediately available or for testing
    // In a real scenario, ensure platformCallback is properly injected.
    public SubscriberRegistryImpl(List<SubscriberDefinition> subscriberDefinitions, DynamicClassLoader dynamicClassLoader) {
        this(subscriberDefinitions, dynamicClassLoader, new PlatformCallback() {
            // Default placeholder callback
            @Override
            public void onRateUpdate(com.toyota.mainapp.model.Rate rate) {
                logger.warn("Placeholder PlatformCallback: Rate received for {} from {}", rate.getSymbol(), rate.getPlatformName());
            }
            @Override
            public void onError(String platformName, String errorMessage, Throwable throwable) {
                logger.warn("Placeholder PlatformCallback: Error from {}: {}", platformName, errorMessage, throwable);
            }
            @Override
            public void onStatusChange(String platformName, String statusMessage) {
                 logger.warn("Placeholder PlatformCallback: Status change from {}: {}", platformName, statusMessage);
            }
        });
        logger.warn("SubscriberRegistry initialized with a placeholder PlatformCallback. Ensure a proper callback is set for full functionality.");
    }


    @PostConstruct
    public void initializeSubscribers() {
        if (platformCallback == null) {
            logger.error("PlatformCallback is not set in SubscriberRegistry. Subscribers will not be able to report data or errors.");
            // Depending on requirements, this could throw an exception or prevent subscribers from loading.
            // For now, it will proceed but log a severe warning.
        }
        logger.info("Initializing subscribers based on {} definitions...", subscriberDefinitions.size());
        for (SubscriberDefinition definition : subscriberDefinitions) {
            if (definition.getClassName() == null || definition.getClassName().trim().isEmpty()) {
                logger.error("Subscriber definition for '{}' is missing a className. Cannot load.", definition.getName());
                continue;
            }
            PlatformSubscriber subscriber = dynamicClassLoader.loadSubscriber(
                    definition.getClassName(),
                    definition,
                    platformCallback // Pass the actual callback
            );
            if (subscriber != null) {
                registerSubscriber(subscriber);
                logger.info("Registered subscriber for platform: {}", subscriber.getPlatformName());
            } else {
                logger.error("Failed to load or initialize subscriber for definition: {}", definition.getName());
            }
        }
    }

    public void registerSubscriber(PlatformSubscriber subscriber) {
        if (subscriber == null || subscriber.getPlatformName() == null) {
            logger.warn("Attempted to register a null subscriber or subscriber with no platform name.");
            return;
        }
        activeSubscribers.put(subscriber.getPlatformName(), subscriber);
        logger.info("PlatformSubscriber '{}' registered.", subscriber.getPlatformName());
    }

    public void unregisterSubscriber(String platformName) {
        PlatformSubscriber subscriber = activeSubscribers.remove(platformName);
        if (subscriber != null) {
            subscriber.stopSubscription(); // Ensure it's stopped
            logger.info("PlatformSubscriber '{}' unregistered and stopped.", platformName);
        }
    }

    public PlatformSubscriber getSubscriber(String platformName) {
        return activeSubscribers.get(platformName);
    }

    public List<PlatformSubscriber> getAllSubscribers() {
        return new ArrayList<>(activeSubscribers.values());
    }

    public List<PlatformSubscriber> getActiveSubscribers() {
        return activeSubscribers.values().stream()
                .filter(PlatformSubscriber::isActive)
                .collect(Collectors.toList());
    }

    public void startAllSubscribers() {
        logger.info("Starting all registered subscribers...");
        activeSubscribers.values().forEach(subscriber -> {
            try {
                if (!subscriber.isActive()) {
                    subscriber.startSubscription();
                }
            } catch (Exception e) {
                logger.error("Error starting subscriber for platform {}: {}", subscriber.getPlatformName(), e.getMessage(), e);
                if (platformCallback != null) {
                    platformCallback.onError(subscriber.getPlatformName(), "Failed to start: " + e.getMessage(), e);
                }
            }
        });
    }

    public void stopAllSubscribers() {
        logger.info("Stopping all registered subscribers...");
        activeSubscribers.values().forEach(subscriber -> {
            try {
                subscriber.stopSubscription();
            } catch (Exception e) {
                logger.error("Error stopping subscriber for platform {}: {}", subscriber.getPlatformName(), e.getMessage(), e);
                 if (platformCallback != null) {
                    platformCallback.onError(subscriber.getPlatformName(), "Failed to stop cleanly: " + e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public boolean startSubscriber(String name) {
        PlatformSubscriber subscriber = activeSubscribers.get(name);
        if (subscriber != null) {
            try {
                subscriber.startSubscription();
                return true;
            } catch (Exception e) {
                logger.error("Error starting subscriber {}: {}", name, e.getMessage(), e);
                if (platformCallback != null) {
                    platformCallback.onError(name, "Failed to start: " + e.getMessage(), e);
                }
            }
        }
        return false;
    }

    @Override
    public boolean stopSubscriber(String name) {
        PlatformSubscriber subscriber = activeSubscribers.get(name);
        if (subscriber != null) {
            try {
                subscriber.stopSubscription();
                return true;
            } catch (Exception e) {
                logger.error("Error stopping subscriber {}: {}", name, e.getMessage(), e);
                if (platformCallback != null) {
                    platformCallback.onError(name, "Failed to stop: " + e.getMessage(), e);
                }
            }
        }
        return false;
    }

    @Override
    public Optional<PlatformSubscriber> getSubscriber(String name) {
        return Optional.ofNullable(activeSubscribers.get(name));
    }
}
