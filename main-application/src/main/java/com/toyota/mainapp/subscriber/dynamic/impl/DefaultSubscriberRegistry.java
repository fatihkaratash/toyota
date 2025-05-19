package com.toyota.mainapp.subscriber.dynamic.impl;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.logging.LoggingHelper;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
import com.toyota.mainapp.subscriber.dynamic.DynamicClassLoader;
import com.toyota.mainapp.subscriber.dynamic.SubscriberRegistry;
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
public class DefaultSubscriberRegistry implements SubscriberRegistry {

    private static final LoggingHelper logger = new LoggingHelper(DefaultSubscriberRegistry.class);

    private final List<SubscriberDefinition> subscriberDefinitions;
    private final DynamicClassLoader dynamicClassLoader;
    private final PlatformCallback platformCallback; // MainCoordinator will implement this

    private final Map<String, PlatformSubscriber> activeSubscribers = new ConcurrentHashMap<>();

    @Autowired
    public DefaultSubscriberRegistry(List<SubscriberDefinition> subscriberDefinitions,
                                     DynamicClassLoader dynamicClassLoader,
                                     PlatformCallback platformCallback) {
        this.subscriberDefinitions = subscriberDefinitions;
        this.dynamicClassLoader = dynamicClassLoader;
        this.platformCallback = platformCallback;
    }

    @Override
    @PostConstruct
    public void initializeSubscribers() {
        logger.info(LoggingHelper.OPERATION_START, LoggingHelper.COMPONENT_SUBSCRIBER, "Tüm aboneler başlatılıyor...");
        if (subscriberDefinitions == null || subscriberDefinitions.isEmpty()) {
            logger.warn(LoggingHelper.OPERATION_INFO, LoggingHelper.COMPONENT_SUBSCRIBER, "Abone tanımı bulunamadı.");
            return;
        }

        for (SubscriberDefinition definition : subscriberDefinitions) {
            if (definition.getClassName() == null || definition.getClassName().trim().isEmpty()) {
                logger.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.COMPONENT_SUBSCRIBER, definition.getName(),
                        "Abone tanımının className'i yok, atlanıyor: " + definition.getName());
                continue;
            }
            PlatformSubscriber subscriber = dynamicClassLoader.loadSubscriber(
                    definition.getClassName(),
                    definition,
                    platformCallback
            );
            if (subscriber != null) {
                activeSubscribers.put(definition.getName(), subscriber);
                logger.info(LoggingHelper.OPERATION_INFO, LoggingHelper.COMPONENT_SUBSCRIBER, definition.getName(),
                        "Abone başarıyla başlatıldı: " + definition.getName());
            } else {
                logger.error(LoggingHelper.OPERATION_ERROR, LoggingHelper.COMPONENT_SUBSCRIBER, definition.getName(),
                        "Abone başlatılamadı: " + definition.getName());
            }
        }
        logger.info(LoggingHelper.OPERATION_STOP, LoggingHelper.COMPONENT_SUBSCRIBER, "Aboneler başlatıldı. Toplam aktif: " + activeSubscribers.size());
    }

    @Override
    public void startAllSubscribers() {
        logger.info(LoggingHelper.OPERATION_START, LoggingHelper.COMPONENT_SUBSCRIBER, "Tüm kayıtlı aboneler başlatılıyor...");
        activeSubscribers.forEach((name, subscriber) -> {
            try {
                if (!subscriber.isActive()) {
                    subscriber.startSubscription();
                }
            } catch (Exception e) {
                logger.error(LoggingHelper.OPERATION_ERROR, LoggingHelper.COMPONENT_SUBSCRIBER, name,
                        "Abone başlatılamadı " + name, e);
                platformCallback.onError(name, "Abone başlatılamadı: " + e.getMessage(), e);
            }
        });
        logger.info(LoggingHelper.OPERATION_STOP, LoggingHelper.COMPONENT_SUBSCRIBER, "Tüm abonelerin başlatılması tamamlandı.");
    }

    @Override
    public void stopAllSubscribers() {
        logger.info(LoggingHelper.OPERATION_START, LoggingHelper.COMPONENT_SUBSCRIBER, "Tüm aktif aboneler durduruluyor...");
        activeSubscribers.forEach((name, subscriber) -> {
            try {
                if (subscriber.isActive()) {
                    subscriber.stopSubscription();
                }
            } catch (Exception e) {
                logger.error(LoggingHelper.OPERATION_ERROR, LoggingHelper.COMPONENT_SUBSCRIBER, name,
                        "Abone durdurulamadı " + name, e);
                 platformCallback.onError(name, "Abone durdurulamadı: " + e.getMessage(), e);
            }
        });
        logger.info(LoggingHelper.OPERATION_STOP, LoggingHelper.COMPONENT_SUBSCRIBER, "Tüm abonelerin durdurulması tamamlandı.");
    }

    @Override
    public boolean startSubscriber(String name) {
        PlatformSubscriber subscriber = activeSubscribers.get(name);
        if (subscriber != null) {
            if (!subscriber.isActive()) {
                try {
                    subscriber.startSubscription();
                    logger.info(LoggingHelper.OPERATION_START, LoggingHelper.COMPONENT_SUBSCRIBER, name, "Abone başlatıldı: " + name);
                    return true;
                } catch (Exception e) {
                    logger.error(LoggingHelper.OPERATION_ERROR, LoggingHelper.COMPONENT_SUBSCRIBER, name, "Abone başlatılamadı " + name, e);
                    platformCallback.onError(name, "Abone başlatılamadı: " + e.getMessage(), e);
                    return false;
                }
            } else {
                logger.warn(LoggingHelper.OPERATION_INFO, LoggingHelper.COMPONENT_SUBSCRIBER, name, "Abone zaten aktif: " + name);
                return true; // Already active is considered a success in this context
            }
        }
        logger.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.COMPONENT_SUBSCRIBER, name, "Başlatılacak abone bulunamadı: " + name);
        return false;
    }

    @Override
    public boolean stopSubscriber(String name) {
        PlatformSubscriber subscriber = activeSubscribers.get(name);
        if (subscriber != null) {
            if (subscriber.isActive()) {
                 try {
                    subscriber.stopSubscription();
                    logger.info(LoggingHelper.OPERATION_STOP, LoggingHelper.COMPONENT_SUBSCRIBER, name, "Abone durduruldu: " + name);
                    return true;
                } catch (Exception e) {
                    logger.error(LoggingHelper.OPERATION_ERROR, LoggingHelper.COMPONENT_SUBSCRIBER, name, "Abone durdurulamadı " + name, e);
                    platformCallback.onError(name, "Abone durdurulamadı: " + e.getMessage(), e);
                    return false;
                }
            } else {
                logger.warn(LoggingHelper.OPERATION_INFO, LoggingHelper.COMPONENT_SUBSCRIBER, name, "Abone zaten durdurulmuş: " + name);
                return true; // Already stopped
            }
        }
        logger.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.COMPONENT_SUBSCRIBER, name, "Durdurulacak abone bulunamadı: " + name);
        return false;
    }

    @Override
    public Optional<PlatformSubscriber> getSubscriber(String name) {
        return Optional.ofNullable(activeSubscribers.get(name));
    }

    @Override
    public List<PlatformSubscriber> getAllSubscribers() {
        return Collections.unmodifiableList(new ArrayList<>(activeSubscribers.values()));
    }
}
