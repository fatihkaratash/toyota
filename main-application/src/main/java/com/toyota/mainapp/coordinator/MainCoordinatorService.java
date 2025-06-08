package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.RealTimeBatchProcessor;
import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.ProviderRateDto;
import com.toyota.mainapp.exception.AggregatedRateValidationException;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.mapper.RateMapper;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.dynamic.DynamicSubscriberLoader;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.toyota.mainapp.config.ApplicationProperties;

/**
Real-time pipeline coordinator with ApplicationProperties integration
 * Clean separation: data acquisition → validation → real-time batch processing
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

    /**
     * Servisi başlat ve aboneleri yükle
     */
    @PostConstruct
    public void initializeAndStartSubscribers() {
        log.info("MainCoordinatorService initializing...");
        
        // Check environment for startup delay
        int startupDelaySeconds = getEnvInt("STARTUP_DELAY_SECONDS", 10);
        
        try {
            log.info("Waiting for {} seconds to allow Kafka to initialize...", startupDelaySeconds);
            Thread.sleep(startupDelaySeconds * 1000L);
            log.info("Proceeding with subscriber initialization.");
        } catch (InterruptedException e) {
            log.warn("Kafka startup delay interrupted.", e);
            Thread.currentThread().interrupt();
        }

        String configPathToUse = appProperties.getSubscribersConfigPath();
        log.info("Aboneler yükleniyor: {}", configPathToUse);
        
        try {
            Collection<PlatformSubscriber> subscribers = dynamicSubscriberLoader.loadSubscribers(configPathToUse, this);
            
            if (subscribers.isEmpty()) {
                log.warn("Hiç abone yüklenemedi");
                return;
            }

            log.info("{} abone başarıyla yüklendi", subscribers.size());
            
            // Her aboneyi kendi iş parçacığında başlat
            subscribers.forEach(subscriber -> {
                String providerName = subscriber.getProviderName();
                activeSubscribers.put(providerName, subscriber);
                
                subscriberTaskExecutor.execute(() -> startSubscriber(subscriber));
            });
            
        } catch (Exception e) {
            log.error("Aboneler yüklenirken hata oluştu", e);
        }
    }
    
    /**
     * Bir aboneyi başlat
     */
    private void startSubscriber(PlatformSubscriber subscriber) {
        String providerName = subscriber.getProviderName();
        try {
            log.info("Abone bağlanıyor: {}", providerName);
            subscriber.connect();
            
            if (subscriber.isConnected()) {
                log.info("Abone ana döngüsü başlatılıyor: {}", providerName);
                subscriber.startMainLoop();
            }
        } catch (Exception e) {
            log.error("Abone çalışması sırasında hata: {}", providerName, e);
            onProviderConnectionStatus(providerName, false, "Bağlantı veya çalışma hatası: " + e.getMessage());
            onProviderError(providerName, "Bağlantı veya çalışma başarısız oldu", e);
        }
    }

    /**
     Immediate real-time pipeline with comprehensive snapshot collection
     */
    @Override
    public void onRateAvailable(String providerName, ProviderRateDto providerRate) {
        pipelineTaskExecutor.execute(() -> {
            try {
                if (providerRate.getProviderName() == null) {
                    providerRate.setProviderName(providerName);
                }

                BaseRateDto baseRate = rateMapper.toBaseRateDto(providerRate);

                String normalizedSymbol = SymbolUtils.normalizeSymbol(baseRate.getSymbol());
                if (!SymbolUtils.isValidSymbol(normalizedSymbol)) {
                    return;
                }
                baseRate.setSymbol(normalizedSymbol);

                rateValidatorService.validate(baseRate);
                baseRate.setValidatedAt(System.currentTimeMillis());

                rateCacheService.cacheRawRate(baseRate);
                kafkaPublishingService.publishRawRate(baseRate);
                realTimeBatchProcessor.processNewRate(baseRate);

            } catch (AggregatedRateValidationException e) {
                log.warn("Rate validation failed from {}: {}", providerName, e.getErrors());
            } catch (Exception e) {
                log.error("Error in pipeline from {}: {}", providerName, e.getMessage());
            }
        });
    }

    @Override
    public void onRateUpdate(String providerName, ProviderRateDto rateUpdate) {
        onRateAvailable(providerName, rateUpdate);
    }

    @Override
    public void onProviderConnectionStatus(String providerName, boolean isConnected, String statusMessage) {
        if (isConnected) {
            log.info("Provider connected {}", providerName);
        } else {
            log.warn("Provider disconnected {}", providerName);
        }
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
        log.info("Abone durduruluyor: {}", providerName);
        
        PlatformSubscriber subscriber = activeSubscribers.get(providerName);
        if (subscriber != null) {
            try {
                subscriber.stopMainLoop();
                subscriber.disconnect();
                activeSubscribers.remove(providerName);
                log.info("Abone başarıyla durduruldu: {}", providerName);
            } catch (Exception e) {
                log.error("Abone durdurulurken hata oluştu: {}", providerName, e);
            }
        } else {
            log.warn("Durdurulamadı, abone bulunamadı: {}", providerName);
        }
    }

    @PreDestroy
    public void shutdownCoordinator() {
        log.info("Koordinatör ve tüm aboneler kapatılıyor");
        activeSubscribers.keySet()
            .forEach(this::stopSubscriber);
            
        log.info("Koordinatör kapatma işlemi tamamlandı");
    }

    private int getEnvInt(String envName, int defaultValue) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid integer value for {}: {}, using default: {}", envName, envValue, defaultValue);
            }
        }
        return defaultValue;
    }
}
