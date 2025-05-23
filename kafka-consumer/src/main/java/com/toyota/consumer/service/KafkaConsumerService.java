
package com.toyota.consumer.service;

import com.toyota.consumer.model.RateDocument; // Added import
import com.toyota.consumer.model.RateEntity;
import com.toyota.consumer.repository.opensearch.RateDocumentRepository; // Added import - ensure this repository exists
import com.toyota.consumer.util.RateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment; // Added import
import org.springframework.kafka.support.KafkaHeaders; // Added import for potential message key/id
import org.springframework.messaging.handler.annotation.Header; // Added import for @Header
import org.springframework.stereotype.Service;

import java.util.Optional; // Added import

/**
 * Service to consume rate messages from Kafka, persist to PostgreSQL, and index to OpenSearch.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final RateParser rateParser;
    private final PersistenceService persistenceService;
    private final RateDocumentRepository rateDocumentRepository; // Added for OpenSearch

    /**
     * Consumes messages from the financial rates topic.
     * Persists to PostgreSQL and then indexes to OpenSearch.
     */
    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.group-id}"
        // Consider adding a specific containerFactory if you have custom error handlers or configurations
    )
    public void consumeRates(String message,
                             // @Header(KafkaHeaders.RECEIVED_KEY, required = false) String key, // Optional: if you need Kafka message key
                             Acknowledgment acknowledgment) {
        log.debug("Received message: {}", message);
        
        try {
            Optional<RateEntity> entityOptional = rateParser.parseToEntity(message); // Assuming parseToEntity returns Optional<RateEntity>
            
            if (entityOptional.isPresent()) {
                RateEntity rateEntity = entityOptional.get();
                
                // 1. Persist to PostgreSQL
                // Assuming saveRate populates the ID in rateEntity or returns the persisted entity with ID
                RateEntity persistedEntity = persistenceService.saveRate(rateEntity);
                log.info("Successfully persisted rate to PostgreSQL: {}", persistedEntity.getRateName());

                // 2. Convert to RateDocument and index to OpenSearch
                // Pass null for messageId if RateEntity.id (from DB) should be used by fromEntity
                // Or pass 'key' if you want to use Kafka message key as OpenSearch document ID
                RateDocument rateDocument = RateDocument.fromEntity(persistedEntity, null); 
                if (rateDocument != null) {
                    rateDocumentRepository.save(rateDocument);
                    log.info("Successfully indexed rate to OpenSearch: {}", rateDocument.getRateName());
                } else {
                    log.warn("RateDocument conversion resulted in null for entity: {}", persistedEntity.getRateName());
                }
                
                acknowledgment.acknowledge();
            } else {
                log.warn("Message could not be parsed, skipping: '{}'", message);
                acknowledgment.acknowledge(); // Acknowledge to skip unparseable messages (or send to DLQ)
            }
        } catch (Exception e) {
            log.error("Error processing or persisting message: '{}'. Not Acknowledging.", message, e);
            // Do not acknowledge, let Kafka's error handling (retry, DLQ) take over
            // acknowledgment.nack(1000); // Or nack for a specific delay if retry is configured
        }
    }
}