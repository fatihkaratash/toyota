package com.toyota.mainapp.coordinator.impl;

import com.toyota.mainapp.cache.RateCache;
import com.toyota.mainapp.calculator.RateCalculator;
import com.toyota.mainapp.coordinator.Coordinator;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.kafka.producer.RateProducer;
import com.toyota.mainapp.kafka.message.RateMessage;
import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateStatus;
import com.toyota.mainapp.subscriber.dynamic.SubscriberRegistry;
import com.toyota.mainapp.validation.RateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main implementation of the Coordinator interface that orchestrates the data flow.
 * This class is the heart of the application, managing the complete life cycle
 * of rate data from reception to validation, calculation, caching, and publication.
 */
@Service
public class MainCoordinator implements Coordinator, PlatformCallback {
    
    private static final Logger logger = LoggerFactory.getLogger(MainCoordinator.class);
    
    private final SubscriberRegistry subscriberRegistry;
    private final RateValidator rateValidator;
    private final RateCache rateCache;
    private final RateCalculator rateCalculator;
    private final RateProducer rateProducer;
    private final String rawRatesTopic;
    private final String calculatedRatesTopic;
    
    private final Map<String, String> statusMessages = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    @Autowired
    public MainCoordinator(SubscriberRegistry subscriberRegistry,
                          RateValidator rateValidator,
                          RateCache rateCache,
                          RateCalculator rateCalculator,
                          RateProducer rateProducer,
                          @Qualifier("rawRatesTopicName") String rawRatesTopic,
                          @Qualifier("calculatedRatesTopicName") String calculatedRatesTopic) {
        this.subscriberRegistry = subscriberRegistry;
        this.rateValidator = rateValidator;
        this.rateCache = rateCache;
        this.rateCalculator = rateCalculator;
        this.rateProducer = rateProducer;
        this.rawRatesTopic = rawRatesTopic;
        this.calculatedRatesTopic = calculatedRatesTopic;
    }
    
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("MainCoordinator başlatılıyor...");
            
            // Start all subscribers
            subscriberRegistry.startAllSubscribers();
            
            logger.info("MainCoordinator başarıyla başlatıldı.");
        } else {
            logger.warn("MainCoordinator zaten çalışıyor.");
        }
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("MainCoordinator durduruluyor...");
            
            // Stop all subscribers
            subscriberRegistry.stopAllSubscribers();
            
            logger.info("MainCoordinator başarıyla durduruldu.");
        } else {
            logger.warn("MainCoordinator zaten durdurulmuş.");
        }
    }
    
    @Override
    public void processRate(Rate rate) {
        if (!running.get()) {
            logger.warn("{} kuru alındı ancak koordinatör çalışmıyor. Yoksayılıyor.", rate.getSymbol());
            return;
        }
        
        logger.debug("Kur işleniyor: {}", rate);
        
        try {
            // Step 1: Validate the rate
            if (!rateValidator.validate(rate)) {
                logger.warn("{} için kur doğrulaması başarısız: {}", rate.getSymbol(), rate);
                // Optionally, publish invalid rates to a separate topic or handle them
                return;
            }
            
            // Step 2: Cache the raw rate
            rateCache.cacheRawRate(rate);
            logger.debug("Ham kur önbelleğe alındı: {}", rate.getSymbol());
            
            // Step 3: Publish the raw rate to Kafka
            publishToKafka(rate, rawRatesTopic);
            
            // Step 4: Calculate derived rates if needed
            if (rateCalculator.shouldCalculate(rate.getSymbol())) {
                logger.debug("{} sembolü için hesaplama tetikleniyor", rate.getSymbol());
                Map<String, CalculatedRate> calculatedRates = rateCalculator.calculateDerivedRates(rate);
                
                // Step 5: Cache calculated rates
                for (CalculatedRate calculatedRate : calculatedRates.values()) {
                    rateCache.cacheCalculatedRate(calculatedRate);
                    logger.debug("Hesaplanmış kur önbelleğe alındı: {}", calculatedRate.getSymbol());
                    
                    // Step 6: Publish calculated rates to Kafka
                    publishToKafka(calculatedRate, calculatedRatesTopic);
                }
            }
            
        } catch (Exception e) {
            logger.error("{} kuru işlenirken hata: {}", rate.getSymbol(), e.getMessage(), e);
        }
    }
    
    private void publishToKafka(Rate rate, String topic) {
        try {
            RateMessage message = new RateMessage(
                rate.getSymbol(),
                rate.getFields().getBid(),
                rate.getFields().getAsk(),
                rate.getFields().getTimestamp()
            );
            
            rateProducer.sendRate(topic, message); // Use the specific topic
            logger.debug("Kur Kafka konusuna yayınlandı {}: {}", topic, rate.getSymbol());
        } catch (Exception e) {
            logger.error("Kur Kafka konusuna yayınlanamadı {}: {}", topic, rate.getSymbol(), e);
        }
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
    // PlatformCallback Implementation
    
    @Override
    public void onRateUpdate(Rate rate) {
        if (rate == null) {
            logger.warn("Platform geri aramasından null kur alındı.");
            return;
        }
        
        logger.debug("{} için {} platformundan kur güncellemesi alındı", 
                    rate.getSymbol(), rate.getPlatformName());
        
        // Process the rate through the main workflow
        processRate(rate);
    }
    
    @Override
    public void onError(String platformName, String errorMessage, Throwable throwable) {
        if (throwable != null) {
            logger.error("{} platformundan hata: {}", platformName, errorMessage, throwable);
        } else {
            logger.error("{} platformundan hata: {}", platformName, errorMessage);
        }
        
        // Store platform status
        statusMessages.put(platformName, "HATA: " + errorMessage);
    }
    
    @Override
    public void onStatusChange(String platformName, String statusMessage) {
        logger.info("{} platformu için durum değişikliği: {}", platformName, statusMessage);
        
        // Store platform status
        statusMessages.put(platformName, statusMessage);
    }
    
    /**
     * Get the latest status for a specific platform
     * 
     * @param platformName Name of the platform
     * @return The latest status message, or null if none exists
     */
    public String getPlatformStatus(String platformName) {
        return statusMessages.get(platformName);
    }
}
