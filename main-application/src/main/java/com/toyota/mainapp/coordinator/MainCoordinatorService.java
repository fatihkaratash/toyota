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

// Import ApplicationProperties if it exists in your project
import com.toyota.mainapp.config.ApplicationProperties;

/**
 * ✅ MODERNIZED: Real-time pipeline coordinator with ApplicationProperties integration
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
     * ✅ ENHANCED: Immediate real-time pipeline with comprehensive snapshot collection
     */
    @Override
    public void onRateAvailable(String providerName, ProviderRateDto providerRate) {
        log.info("📊 Raw data received from {}: Symbol={}, Bid={}, Ask={}", 
                providerName, providerRate.getSymbol(), providerRate.getBid(), providerRate.getAsk());
        
        // ✅ IMMEDIATE PROCESSING: Use dedicated pipeline executor for instant response
        pipelineTaskExecutor.execute(() -> {
            try {
                // Set provider name if missing
                if (providerRate.getProviderName() == null) {
                    providerRate.setProviderName(providerName);
                }

                // 1. Convert to BaseRateDto
                BaseRateDto baseRate = rateMapper.toBaseRateDto(providerRate);
                log.debug("✅ ProviderRateDto converted to BaseRateDto: {}", baseRate);

                // 2. Symbol normalization and validation
                String normalizedSymbol = SymbolUtils.normalizeSymbol(baseRate.getSymbol());
                if (!SymbolUtils.isValidSymbol(normalizedSymbol)) {
                    log.warn("❌ Invalid symbol format, skipping immediate pipeline: '{}'", baseRate.getSymbol());
                    return;
                }
                baseRate.setSymbol(normalizedSymbol);

                // 3. Rate validation
                rateValidatorService.validate(baseRate);
                baseRate.setValidatedAt(System.currentTimeMillis());
                log.debug("✅ Rate validation successful: {}", normalizedSymbol);

                // 4. Cache raw rate
                rateCacheService.cacheRawRate(baseRate);
                log.info("✅ Rate cached successfully: {}, provider: {}", 
                        normalizedSymbol, providerName);

                // 5. Publish to individual raw rate topic
                kafkaPublishingService.publishRawRate(baseRate);

                // 6. ✅ TRIGGER IMMEDIATE PIPELINE: Each rate triggers complete snapshot processing
                realTimeBatchProcessor.processNewRate(baseRate);
                log.debug("🚀 Immediate pipeline triggered for snapshot generation: {}", normalizedSymbol);

            } catch (AggregatedRateValidationException e) {
                log.warn("⚠️ Rate validation failed from {}: Symbol={}, Errors={}", 
                        providerName, providerRate.getSymbol(), e.getErrors());
            } catch (Exception e) {
                log.error("❌ Error in immediate pipeline from {}: Symbol={}", 
                        providerName, providerRate.getSymbol(), e);
            }
        });
    }

    /**
     * Kur güncellemelerini işle
     */
    @Override
    public void onRateUpdate(String providerName, ProviderRateDto rateUpdate) {
        onRateAvailable(providerName, rateUpdate);
    }

    /**
     * Sağlayıcı bağlantı durumu değişikliklerini işle
     */
    @Override
    public void onProviderConnectionStatus(String providerName, boolean isConnected, String statusMessage) {
        if (isConnected) {
            log.info("Sağlayıcı bağlandı {}: {}", providerName, statusMessage);
        } else {
            log.warn("Sağlayıcı bağlantısı kesildi {}: {}", providerName, statusMessage);
        }
    }

    /**
     * Kur durumu güncellemelerini işle
     */
    @Override
    public void onRateStatus(String providerName, BaseRateDto statusRate) {
        log.info("Kur durumu güncellendi, sağlayıcı: {}, durum: {}", providerName, statusRate.getStatus());
        
        kafkaPublishingService.publishRate(statusRate);
        
        if (statusRate.getStatus() == BaseRateDto.RateStatusEnum.ERROR) {
            log.warn("{} sembolü için {} sağlayıcısından hata durumu algılandı", 
                    statusRate.getSymbol(), statusRate.getProviderName());
        }
    }
    
    /**
     * Sağlayıcı hatalarını işle
     */
    @Override
    public void onProviderError(String providerName, String errorMessage, Throwable throwable) {
        log.error("Sağlayıcıdan hata {}: {}", providerName, errorMessage, throwable);
    }

    /**
     * Bir aboneyi durdur
     */
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

    /**
     * Servis sonlandırılırken tüm aboneleri durdur
     */
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
