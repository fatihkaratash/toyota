package com.toyota.mainapp.subscriber.dynamic.impl;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
import com.toyota.mainapp.subscriber.dynamic.DynamicClassLoader;
import com.toyota.mainapp.subscriber.dynamic.SubscriberRegistry;
import com.toyota.mainapp.util.LoggingHelper;
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

@Service
public class DefaultSubscriberRegistry implements SubscriberRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSubscriberRegistry.class);

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
        logger.info("Tüm aboneler başlatılıyor...");
        if (subscriberDefinitions == null || subscriberDefinitions.isEmpty()) {
            logger.warn("Abone tanımı bulunamadı.");
            return;
        }

        for (SubscriberDefinition definition : subscriberDefinitions) {
            if (definition.getClassName() == null || definition.getClassName().trim().isEmpty()) {
                logger.warn("Abone tanımının className'i yok, atlanıyor: {}", definition.getName());
                continue;
            }
            PlatformSubscriber subscriber = dynamicClassLoader.loadSubscriber(
                    definition.getClassName(),
                    definition,
                    platformCallback
            );
            if (subscriber != null) {
                activeSubscribers.put(definition.getName(), subscriber);
                logger.info("Abone başarıyla başlatıldı: {}", definition.getName());
            } else {
                logger.error("Abone başlatılamadı: {}", definition.getName());
            }
        }
        logger.info("Aboneler başlatıldı. Toplam aktif: {}", activeSubscribers.size());
    }

    @Override
    public void startAllSubscribers() {
        logger.info("Tüm kayıtlı aboneler başlatılıyor...");
        activeSubscribers.forEach((name, subscriber) -> {
            try {
                if (!subscriber.isActive()) {
                    subscriber.startSubscription();
                }
            } catch (Exception e) {
                logger.error("Abone başlatılamadı {}: {}", name, e.getMessage(), e);
                platformCallback.onError(name, "Abone başlatılamadı: " + e.getMessage(), e);
            }
        });
        logger.info("Tüm abonelerin başlatılması tamamlandı.");
    }

    @Override
    public void stopAllSubscribers() {
        logger.info("Tüm aktif aboneler durduruluyor...");
        activeSubscribers.forEach((name, subscriber) -> {
            try {
                if (subscriber.isActive()) {
                    subscriber.stopSubscription();
                }
            } catch (Exception e) {
                logger.error("Abone durdurulamadı {}: {}", name, e.getMessage(), e);
                platformCallback.onError(name, "Abone durdurulamadı: " + e.getMessage(), e);
            }
        });
        logger.info("Tüm abonelerin durdurulması tamamlandı.");
    }

    @Override
    public boolean startSubscriber(String name) {
        PlatformSubscriber subscriber = activeSubscribers.get(name);
        if (subscriber != null) {
            if (!subscriber.isActive()) {
                try {
                    subscriber.startSubscription();
                    logger.info("Abone başlatıldı: {}", name);
                    return true;
                } catch (Exception e) {
                    logger.error("Abone başlatılamadı {}: {}", name, e.getMessage(), e);
                    platformCallback.onError(name, "Abone başlatılamadı: " + e.getMessage(), e);
                    return false;
                }
            } else {
                logger.warn("Abone zaten aktif: {}", name);
                return true; // Already active is considered a success in this context
            }
        }
        logger.warn("Başlatılacak abone bulunamadı: {}", name);
        return false;
    }

    @Override
    public boolean stopSubscriber(String name) {
        PlatformSubscriber subscriber = activeSubscribers.get(name);
        if (subscriber != null) {
            if (subscriber.isActive()) {
                try {
                    subscriber.stopSubscription();
                    logger.info("Abone durduruldu: {}", name);
                    return true;
                } catch (Exception e) {
                    logger.error("Abone durdurulamadı {}: {}", name, e.getMessage(), e);
                    platformCallback.onError(name, "Abone durdurulamadı: " + e.getMessage(), e);
                    return false;
                }
            } else {
                logger.warn("Abone zaten durdurulmuş: {}", name);
                return true; // Already stopped
            }
        }
        logger.warn("Durdurulacak abone bulunamadı: {}", name);
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
