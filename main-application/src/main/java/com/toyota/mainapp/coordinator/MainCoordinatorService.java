package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.aggregator.TwoWayWindowAggregator;
import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.RateCalculatorService;
import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.NormalizedRateDto;
import com.toyota.mainapp.dto.ProviderRateDto;
import com.toyota.mainapp.dto.RateStatusDto;
import com.toyota.mainapp.dto.RawRateDto;
import com.toyota.mainapp.dto.payload.RawRatePayloadDto;
import com.toyota.mainapp.exception.AggregatedRateValidationException;
import com.toyota.mainapp.kafka.producer.KafkaRateProducer;
import com.toyota.mainapp.kafka.producer.SimpleFormatKafkaProducer;
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
    private final KafkaRateProducer kafkaRateProducer;
    private final SimpleFormatKafkaProducer simpleFormatProducer;
    private final RateCalculatorService rateCalculatorService;
    private final TwoWayWindowAggregator aggregator; // Yeni eklenen toplayıcı

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
     * Abonelerden gelen kur verilerini işle
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

            // 1. Veriyi standartlaştır
            NormalizedRateDto normalizedRate = rateMapper.toNormalizedDto(providerRate);
            log.debug("Standartlaştırılmış kur: {}", normalizedRate);

            // 2. Veriyi doğrula
            rateValidatorService.validate(normalizedRate);
            log.debug("Kur doğrulama başarılı: {}", normalizedRate.getSymbol());
            
            // 3. Önbellekleme ve Kafka için ham kur oluştur
            RawRateDto rawRate = rateMapper.toRawDto(normalizedRate);
            rawRate.setReceivedAt(System.currentTimeMillis());
            rawRate.setValidatedAt(System.currentTimeMillis());
            
            // 4. Ham kuru önbelleğe al
            String rawRateCacheKey = rawRate.getProviderName() + "_" + rawRate.getSymbol();
            log.debug("Önbellek anahtarı oluşturuldu: {}", rawRateCacheKey);
            rateCacheService.cacheRawRate(rawRateCacheKey, rawRate);
            log.info("Kur başarıyla işlendi ve önbelleğe alındı: {}, sağlayıcı: {}", 
                    rawRate.getSymbol(), providerName);

            // 5. Ham kuru Kafka'ya gönder - JSON formatı
            RawRatePayloadDto payload = rateMapper.toRawRatePayloadDto(rawRate);
            kafkaRateProducer.sendRawRate(payload);
            
            // 5b. Ham kuru Kafka'ya gönder - Basit metin formatı
            simpleFormatProducer.sendRawRate(rawRate);
            log.debug("Kur Kafka'ya gönderildi: {}", rawRateCacheKey);

            // 6. YENİ: Toplayıcıya gönder - pencere bazlı hesaplama için
            log.debug("Kur toplayıcıya gönderiliyor: {}", rawRateCacheKey);
            aggregator.accept(rawRate);

            // 7. Hesaplama motorunu tetikle - mevcut yöntem de tutulabilir veya kaldırılabilir
            log.info("Güncellenen ham kura dayalı hesaplamalar tetikleniyor: {}", rawRateCacheKey);
            rateCalculatorService.processRateUpdate(rawRateCacheKey, true);

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
    public void onRateStatus(String providerName, RateStatusDto rateStatus) {
        log.info("Kur durumu güncellendi, sağlayıcı: {}, durum: {}", providerName, rateStatus);
        
        if (rateStatus.getStatus() == RateStatusDto.RateStatusEnum.ERROR) {
            log.warn("{} sembolü için {} sağlayıcısından hata durumu algılandı", 
                    rateStatus.getSymbol(), rateStatus.getProviderName());
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
