package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.aggregator.TwoWayWindowAggregator;
import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.ProviderRateDto;
import com.toyota.mainapp.dto.RateType;
import com.toyota.mainapp.exception.AggregatedRateValidationException;
import com.toyota.mainapp.kafka.publisher.SequentialPublisher;
import com.toyota.mainapp.mapper.RateMapper;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.dynamic.DynamicSubscriberLoader;
import com.toyota.mainapp.validation.RateValidatorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ana koordinasyon servisi - veri akışını yöneten ve işlemleri koordine eden servis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MainCoordinatorService implements PlatformCallback {

    private final DynamicSubscriberLoader dynamicSubscriberLoader;
    @Qualifier("subscriberTaskExecutor")
    private final TaskExecutor subscriberTaskExecutor;
    private final RateMapper rateMapper;
    private final RateValidatorService rateValidatorService;
    private final RateCacheService rateCacheService;
    private final SequentialPublisher sequentialPublisher;
    private final TwoWayWindowAggregator aggregator;

    @Value("${subscribers.config.path}")
    private String subscribersConfigPath;
    
    private final Map<String, PlatformSubscriber> activeSubscribers = new ConcurrentHashMap<>();

    /**
     * Servisi başlat ve aboneleri yükle
     */
    @PostConstruct
    public void initializeAndStartSubscribers() {
        log.info("MainCoordinatorService initializing...");
        try {
            // TEMPORARY: Add a delay to allow Kafka to fully start before KafkaAdmin client tries to connect.
            // Remove or replace with a proper health check or retry mechanism for production.
            log.info("Waiting for 15 seconds to allow Kafka to initialize...");
            Thread.sleep(15000); // 15 seconds delay
            log.info("Proceeding with subscriber initialization.");
        } catch (InterruptedException e) {
            log.warn("Kafka startup delay interrupted.", e);
            Thread.currentThread().interrupt();
        }

        log.info("Aboneler yükleniyor: {}", subscribersConfigPath);
        
        try {
            Collection<PlatformSubscriber> subscribers = dynamicSubscriberLoader.loadSubscribers(subscribersConfigPath, this);
            
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
     * Abonelerden gelen kur verilerini işle - BaseRateDto ile çalışacak şekilde güncellendi
     */
    @Override
    public void onRateAvailable(String providerName, ProviderRateDto providerRate) {
        log.info("Sağlayıcıdan veri alındı {}: Symbol={}, Bid={}, Ask={}", 
                providerName, providerRate.getSymbol(), providerRate.getBid(), providerRate.getAsk());
        
        try {
            // Sağlayıcı adını ayarla
            if (providerRate.getProviderName() == null) {
                providerRate.setProviderName(providerName);
            }

            // 1. Veriyi doğrudan BaseRateDto'ya dönüştür
            BaseRateDto baseRate = rateMapper.toBaseRateDto(providerRate);
            log.debug("ProviderRateDto'dan BaseRateDto oluşturuldu: {}", baseRate);

            // 2. Veriyi doğrula
            rateValidatorService.validate(baseRate);
            baseRate.setValidatedAt(System.currentTimeMillis());
            log.debug("Kur doğrulama başarılı: {}", baseRate.getSymbol());
            
            // 3. Ham kuru önbelleğe al
            String rateCacheKey = baseRate.getProviderName() + "_" + baseRate.getSymbol();
            log.debug("Önbellek anahtarı oluşturuldu: {}", rateCacheKey);
            rateCacheService.cacheRawRate(rateCacheKey, baseRate);
            
            log.info("Kur başarıyla işlendi ve önbelleğe alındı: {}, sağlayıcı: {}", 
                    baseRate.getSymbol(), providerName);

            // 4. Kuru Kafka'ya gönder
            sequentialPublisher.publishRate(baseRate);
            log.debug("Kur Kafka'ya gönderildi: {}", rateCacheKey);

            // 5. Toplayıcıya gönder - tek hesaplama yaklaşımı olarak
            log.debug("Kur toplayıcıya gönderiliyor: {}", rateCacheKey);
            aggregator.accept(baseRate);

        } catch (AggregatedRateValidationException e) {
            log.warn("{} sağlayıcısından gelen veri doğrulanamadı: Sembol={}, Hatalar={}", 
                    providerName, providerRate.getSymbol(), e.getErrors());
        } catch (Exception e) {
            log.error("{} sağlayıcısından gelen veri işlenirken hata oluştu: Sembol={}", 
                    providerName, providerRate.getSymbol(), e);
        }
    }

    /**
     * Kur güncellemelerini işle
     */
    @Override
    public void onRateUpdate(String providerName, ProviderRateDto rateUpdate) {
        // Güncellemeleri yeni veri gibi işle
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
        
        // Directly publish to Kafka since we already have a BaseRateDto
        sequentialPublisher.publishRate(statusRate);
        
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
        
        // Tüm aboneleri güvenli bir şekilde durdur
        activeSubscribers.keySet()
            .forEach(this::stopSubscriber);
            
        log.info("Koordinatör kapatma işlemi tamamlandı");
    }
}
