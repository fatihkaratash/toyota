// filepath: c:\Projects\toyota\kafka-consumer\src\main\java\com\toyota\consumer\service\KafkaConsumerService.java
package com.toyota.consumer.service;

import com.toyota.consumer.model.RateEntity;
import com.toyota.consumer.util.RateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Optional;
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final PersistenceService persistenceService;
    private final RateParser rateParser;
    
    // USDTRY listener
    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.group-id-usdtry}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUsdTryRates(String message, Acknowledgment acknowledgment) {
        try {
            Optional<RateEntity> entityOptional = rateParser.parseToEntity(message);
            
            if (entityOptional.isPresent()) {
                RateEntity rateEntity = entityOptional.get();
                
                // Sadece USDTRY ile ilgili olanları işle
                if (isUsdTryRate(rateEntity.getRateName())) {
                    // PostgreSQL'e kaydet
                    RateEntity persistedEntity = persistenceService.saveRate(rateEntity);
                    log.info("USDTRY birim kaydedildi: {} - Bid: {}, Ask: {}", 
                            persistedEntity.getRateName(), 
                            persistedEntity.getBid(), 
                            persistedEntity.getAsk());
                    
                    // Ortalamaysa özel işleme
                    if (persistedEntity.getRateName().contains("_AVG")) {
                        log.info("USDTRY ORTALAMA: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
                    }
                }
                acknowledgment.acknowledge();
            } else {
                log.warn("Mesaj ayrıştırılamadı, atlanıyor: '{}'", message);
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            log.error("Mesaj işlenirken hata oluştu: '{}'. Acknowledge edilmiyor.", message, e);
            // Burada acknowledge edilmezse, mesaj tekrar işlenecektir
        }
    }
    
    // EURUSD listener
   // EURTRY listener (önceki EURUSD)
@KafkaListener(
    topics = "${app.kafka.topic.simple-rates}",
    groupId = "${app.kafka.consumer.group-id-eurtry}",  // Bu ID application.yml'de güncellendi
    containerFactory = "kafkaListenerContainerFactory"
)
public void consumeEurTryRates(String message, Acknowledgment acknowledgment) {
    try {
        Optional<RateEntity> entityOptional = rateParser.parseToEntity(message);
        
        if (entityOptional.isPresent()) {
            RateEntity rateEntity = entityOptional.get();
            
            // Sadece EURTRY ile ilgili olanları işle
            if (isEurTryRate(rateEntity.getRateName())) {
                // PostgreSQL'e kaydet
                RateEntity persistedEntity = persistenceService.saveRate(rateEntity);
                log.info("EURTRY birim kaydedildi: {} - Bid: {}, Ask: {}", 
                        persistedEntity.getRateName(), 
                        persistedEntity.getBid(), 
                        persistedEntity.getAsk());
                
                // Ortalamaysa özel işleme
                if (persistedEntity.getRateName().contains("_AVG")) {
                    log.info("EURTRY ORTALAMA: {} - Bid: {}, Ask: {}", 
                            persistedEntity.getRateName(), 
                            persistedEntity.getBid(), 
                            persistedEntity.getAsk());
                }
            }
            acknowledgment.acknowledge();
        } else {
            log.warn("Mesaj ayrıştırılamadı, atlanıyor: '{}'", message);
            acknowledgment.acknowledge();
        }
    } catch (Exception e) {
        log.error("Mesaj işlenirken hata oluştu: '{}'. Acknowledge edilmiyor.", message, e);
    }
}


    
    // GBPTRY listener
    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.group-id-gbptry}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeGbpTryRates(String message, Acknowledgment acknowledgment) {
        try {
            Optional<RateEntity> entityOptional = rateParser.parseToEntity(message);
            
            if (entityOptional.isPresent()) {
                RateEntity rateEntity = entityOptional.get();
                
                // Sadece GBPTRY ve ilişkili olanları işle (GBPUSD ve ilgili çapraz kurları dahil)
                if (isGbpRelatedRate(rateEntity.getRateName())) {
                    // PostgreSQL'e kaydet
                    RateEntity persistedEntity = persistenceService.saveRate(rateEntity);
                    log.info("GBP ilişkili birim kaydedildi: {} - Bid: {}, Ask: {}", 
                            persistedEntity.getRateName(), 
                            persistedEntity.getBid(), 
                            persistedEntity.getAsk());
                    
                    // GBPTRY ise özel işleme
                    if (persistedEntity.getRateName().equals("GBPTRY_AVG") || 
                        persistedEntity.getRateName().equals("GBP/TRY")) {
                        log.info("GBPTRY ÇAPRAZ KUR: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
                    }
                }
                acknowledgment.acknowledge();
            } else {
                log.warn("Mesaj ayrıştırılamadı, atlanıyor: '{}'", message);
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            log.error("Mesaj işlenirken hata oluştu: '{}'. Acknowledge edilmiyor.", message, e);
        }
    }
    
    // Yardımcı Metodlar
    private boolean isUsdTryRate(String rateName) {
        return rateName.contains("USDTRY") || 
               rateName.contains("USD/TRY") || 
               rateName.contains("PF1_USDTRY") || 
               rateName.contains("PF2_USDTRY");
    }
    
   // Yardımcı metodu da değiştir:
    private boolean isEurTryRate(String rateName) {
    return rateName.contains("EURTRY") || 
           rateName.contains("EUR/TRY") || 
           rateName.contains("PF1_EURTRY") || 
           rateName.contains("PF2_EURTRY") ||
           // Çapraz kur hesaplanmış değerler için:
           rateName.contains("EURTRY_AVG");
}
    
    private boolean isGbpRelatedRate(String rateName) {
        return rateName.contains("GBPTRY") || 
               rateName.contains("GBP/TRY") || 
               rateName.contains("GBPUSD") || 
               rateName.contains("GBP/USD") || 
               rateName.contains("PF1_GBPUSD") || 
               rateName.contains("PF2_GBPUSD");
    }
}