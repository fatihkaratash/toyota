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

    private final RateParser rateParser;
    private final PersistenceService persistenceService;
    // private final RateDocumentRepository rateDocumentRepository; // Bu satırı kaldır

    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.group-id}"
    )
    public void consumeRates(String message, Acknowledgment acknowledgment) {
        log.debug("Received message: {}", message);
        
        try {
            Optional<RateEntity> entityOptional = rateParser.parseToEntity(message);
            
            if (entityOptional.isPresent()) {
                RateEntity rateEntity = entityOptional.get();
                
                // PostgreSQL'e kaydet
                RateEntity persistedEntity = persistenceService.saveRate(rateEntity);
                log.info("Successfully persisted rate to PostgreSQL: {}", persistedEntity.getRateName());

                // OpenSearch'e indeksleme kısmını kaldır
                // RateDocument rateDocument = RateDocument.fromEntity(persistedEntity, null); 
                // if (rateDocument != null) {
                //     rateDocumentRepository.save(rateDocument);
                //     log.info("Successfully indexed rate to OpenSearch: {}", rateDocument.getRateName());
                // } else {
                //     log.warn("RateDocument conversion resulted in null for entity: {}", persistedEntity.getRateName());
                // }
                
                acknowledgment.acknowledge();
            } else {
                log.warn("Message could not be parsed, skipping: '{}'", message);
                acknowledgment.acknowledge(); // Ayrıştırılamayan mesajları acknowledge et (veya DLQ'ya gönder)
            }
        } catch (Exception e) {
            log.error("Error processing or persisting message: '{}'. Not Acknowledging.", message, e);
            // Acknowledge etme, Kafka'nın hata işleme mekanizmasını çalıştır (yeniden deneme, DLQ)
        }
    }
}