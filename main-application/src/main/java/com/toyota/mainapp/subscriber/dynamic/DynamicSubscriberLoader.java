package com.toyota.mainapp.subscriber.dynamic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.exception.SubscriberInitializationException;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.api.SubscriberConfigDto;
import com.toyota.mainapp.subscriber.impl.RestRateSubscriber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
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
    
    @Autowired(required = false)
    private WebClient.Builder webClientBuilder;

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
                log.warn("Yapılandırma dosyasında abone bulunamadı: {}", configPath);
                return subscribers;
            }
            
            log.info("{} abone konfigürasyonu okundu", configs.size());
            
            for (SubscriberConfigDto config : configs) {
                if (!config.isEnabled()) {
                    log.info("Devre dışı abone atlanıyor: {}", config.getName());
                    continue;
                }
                
                try {
                    PlatformSubscriber subscriber = createSubscriberInstance(config, callback);
                    subscribers.add(subscriber);
                    log.info("Abone başarıyla oluşturuldu: {}", config.getName());
                } catch (Exception e) {
                    log.error("Abone oluşturulamadı: {} - Hata: {}", config.getName(), e.getMessage(), e);
                }
            }
            
            log.info("Toplam {} abone yüklendi", subscribers.size());
            
        } catch (Exception e) {
            log.error("Aboneler yüklenirken hata oluştu: {} - Hata: {}", configPath, e.getMessage(), e);
        }
        
        return subscribers;
    }

    /**
     * Yapılandırma dosyasını oku
     */
    private List<SubscriberConfigDto> readConfiguration(String configPath) throws IOException {
        Resource resource = resourceLoader.getResource(configPath);
        
        if (!resource.exists()) {
            log.error("Yapılandırma dosyası bulunamadı: {}", configPath);
            return Collections.emptyList();
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            // Create a copy of ObjectMapper with default typing disabled
            ObjectMapper localMapper = objectMapper.copy();
            // This is the key step - disable type info which causes JSON parsing problems
            localMapper.deactivateDefaultTyping();
            
            // Daha ayrıntılı hata ayıklama için
            log.debug("Yapılandırma dosyasının içeriğini okuma: {}", configPath);
            String jsonContent = new String(inputStream.readAllBytes());
            log.debug("Okunan JSON içeriği: {}", jsonContent);
            
            // Parsed configuration to return
            List<SubscriberConfigDto> configs;
            
            try {
                // Parse the config
                configs = localMapper.readValue(jsonContent, 
                    new TypeReference<List<SubscriberConfigDto>>() {});
                    
                log.debug("Yapılandırma başarıyla okundu, {} abone yapılandırması bulundu", configs.size());
                return configs;
            } catch (Exception e) {
                log.error("JSON okuma hatası: {}", e.getMessage(), e);
                throw e;
            }
        } catch (IOException e) {
            log.error("Yapılandırma dosyası okunamadı: {}", configPath, e);
            throw e;
        }
    }

    /**
     * Abone örneği oluştur
     */
    private PlatformSubscriber createSubscriberInstance(SubscriberConfigDto config, PlatformCallback callback) 
            throws ReflectiveOperationException {
        
        if (config.getImplementationClass() == null || config.getImplementationClass().isEmpty()) {
            throw new IllegalArgumentException("Uygulama sınıfı belirtilmemiş: " + config.getName());
        }
        
        // RestRateSubscriber için özel işlem
        if (config.getImplementationClass().contains("RestRateSubscriber")) {
            log.debug("RestRateSubscriber için WebClient.Builder ile özel oluşturma: {}", config.getName());
            RestRateSubscriber subscriber = webClientBuilder != null ? 
                new RestRateSubscriber(webClientBuilder) : new RestRateSubscriber();
            subscriber.init(config, callback);
            return subscriber;
        }
        
        // Diğer tüm aboneler için standart oluşturma
        Class<?> subscriberClass = Class.forName(config.getImplementationClass());
        
        if (!PlatformSubscriber.class.isAssignableFrom(subscriberClass)) {
            throw new IllegalArgumentException(
                "Sınıf PlatformSubscriber arayüzünü uygulamalıdır: " + subscriberClass.getName());
        }
        
        PlatformSubscriber subscriber = (PlatformSubscriber) subscriberClass.getDeclaredConstructor().newInstance();
        subscriber.init(config, callback);
        
        return subscriber;
    }
}
