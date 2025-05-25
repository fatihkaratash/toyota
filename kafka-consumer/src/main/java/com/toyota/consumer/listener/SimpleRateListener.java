package com.toyota.consumer.listener;

import com.toyota.consumer.model.RateEntity;
import com.toyota.consumer.service.PersistenceService;
import com.toyota.consumer.util.RateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class SimpleRateListener {

    private final PersistenceService persistenceService;
    private final RateParser rateParser;
    
    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRateMessage(String message, Acknowledgment acknowledgment) {
        try {
            Optional<RateEntity> entityOptional = rateParser.parseToEntity(message);
            
            if (entityOptional.isPresent()) {
                RateEntity rateEntity = entityOptional.get();
                RateEntity persistedEntity = persistenceService.saveRate(rateEntity);
                
                // Çeşitli kur tipleri için özel loglama
                String rateType = determineRateType(persistedEntity.getRateName());
                switch (rateType) {
                    case "USDTRY":
                        if (persistedEntity.getRateName().contains("_AVG")) {
                            log.info("USDTRY ORTALAMA: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
                        } else {
                            log.info("USDTRY birim kaydedildi: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
                        }
                        break;
                    case "EURTRY":
                        if (persistedEntity.getRateName().contains("_AVG")) {
                            log.info("EURTRY ORTALAMA: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
                        } else {
                            log.info("EURTRY birim kaydedildi: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
                        }
                        break;
                    case "GBPTRY":
                        if (persistedEntity.getRateName().equals("GBPTRY_AVG") || 
                            persistedEntity.getRateName().equals("GBP/TRY")) {
                            log.info("GBPTRY ÇAPRAZ KUR: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
                        } else {
                            log.info("GBP ilişkili birim kaydedildi: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
                        }
                        break;
                    default:
                        log.info("Birim kaydedildi: {} - Bid: {}, Ask: {}", 
                                persistedEntity.getRateName(), 
                                persistedEntity.getBid(), 
                                persistedEntity.getAsk());
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
    
    // Kur adından kur tipini belirle
    private String determineRateType(String rateName) {
        if (rateName == null) return "UNKNOWN";
        
        if (rateName.contains("USDTRY") || rateName.contains("USD/TRY") || 
            rateName.contains("PF1_USDTRY") || rateName.contains("PF2_USDTRY")) {
            return "USDTRY";
        }
        
        if (rateName.contains("EURTRY") || rateName.contains("EUR/TRY") || 
            rateName.contains("PF1_EURTRY") || rateName.contains("PF2_EURTRY") ||
            rateName.equals("EurTryScriptCalculator_EUR/TRY")) {
            return "EURTRY";
        }

        if (rateName.contains("GBPTRY") || rateName.contains("GBP/TRY") || 
            rateName.contains("GBPUSD") || rateName.contains("GBP/USD") || 
            rateName.contains("PF1_GBPUSD") || rateName.contains("PF2_GBPUSD") ||
            rateName.equals("GbpTryScriptCalculator_GBP/TRY")) {
            return "GBPTRY";
        }
        
        return "OTHER";
    }
}