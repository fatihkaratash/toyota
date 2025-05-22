package com.toyota.mainapp.subscriber.dynamic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.exception.SubscriberInitializationException;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.api.SubscriberConfigDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Yapılandırma dosyalarından aboneleri dinamik olarak yükleyen sınıf
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicSubscriberLoader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    /**
     * Aboneleri yapılandırma dosyasından yükle
     * 
     * @param configPath Yapılandırma dosyası yolu
     * @param callback Platform olayları için callback
     * @return Yüklenen abonelerin listesi
     */
    @CircuitBreaker(name = "subscriberLoader")
    @Retry(name = "loaderRetry")
    public List<PlatformSubscriber> loadSubscribers(String configPath, PlatformCallback callback) {
        List<PlatformSubscriber> subscribers = new ArrayList<>();

        try {
            // Yapılandırma dosyasından konfigürasyonları oku
            List<SubscriberConfigDto> configs = readConfiguration(configPath);

            if (configs.isEmpty()) {
                log.warn("Yapılandırma dosyasında abone bulunamadı: {}", configPath);
                return subscribers;
            }

            log.info("{} abone konfigürasyonu okundu", configs.size());

            // Her konfigürasyon için abone örneği oluştur
            for (SubscriberConfigDto config : configs) {
                if (!config.isEnabled()) {
                    log.info("Devre dışı abone atlanıyor: {}", config.getName());
                    continue;
                }

                try {
                    PlatformSubscriber subscriber = instantiateSubscriber(config, callback);
                    subscribers.add(subscriber);
                    log.info("Abone başarıyla oluşturuldu: {}", config.getName());
                } catch (Exception e) {
                    log.error("Abone oluşturulamadı: {}", config.getName(), e);
                }
            }

            log.info("Toplam {} abone yüklendi", subscribers.size());
            
        } catch (Exception e) {
            log.error("Aboneler yüklenirken hata oluştu: {}", configPath, e);
        }

        return subscribers;
    }

    /**
     * Yapılandırma dosyasından konfigürasyonları oku
     */
    private List<SubscriberConfigDto> readConfiguration(String configPath) {
        try {
            Resource resource = resourceLoader.getResource(configPath);
            
            if (!resource.exists()) {
                log.error("Yapılandırma dosyası bulunamadı: {}", configPath);
                return Collections.emptyList();
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                // Create a dedicated ObjectMapper instance without type information for reading subscribers.json
                ObjectMapper subscriberMapper = createSubscriberObjectMapper();
                return subscriberMapper.readValue(inputStream, new TypeReference<List<SubscriberConfigDto>>() {});
            }
        } catch (Exception e) {
            log.error("Yapılandırma dosyası okunamadı: {}", configPath, e);
            throw new SubscriberInitializationException("Abone yapılandırması okunamadı: " + configPath, e);
        }
    }
    
    /**
     * Creates a basic ObjectMapper without type information, specifically for subscribers.json
     */
    private ObjectMapper createSubscriberObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // No need to activate default typing - we want to read plain JSON without type info
        return mapper;
    }

    /**
     * Konfigürasyona göre abone örneği oluştur
     */
    private PlatformSubscriber instantiateSubscriber(SubscriberConfigDto config, PlatformCallback callback) {
        try {
            if (config.getImplementationClass() == null || config.getImplementationClass().trim().isEmpty()) {
                throw new IllegalArgumentException("Uygulama sınıfı belirtilmemiş: " + config.getName());
            }
            
            Class<?> subscriberClass = Class.forName(config.getImplementationClass());
            
            if (!PlatformSubscriber.class.isAssignableFrom(subscriberClass)) {
                throw new IllegalArgumentException("Sınıf PlatformSubscriber arayüzünü uygulamalıdır: " + subscriberClass.getName());
            }
            
            PlatformSubscriber subscriber = (PlatformSubscriber) subscriberClass.getDeclaredConstructor().newInstance();
            subscriber.init(config, callback);
            
            return subscriber;
        } catch (ClassNotFoundException e) {
            throw new SubscriberInitializationException("Abone sınıfı bulunamadı: " + config.getImplementationClass(), e);
        } catch (Exception e) {
            throw new SubscriberInitializationException("Abone örneği oluşturulamadı: " + config.getName(), e);
        }
    }
}
