package com.toyota.consumer.listener;

import com.toyota.consumer.model.RateEntity;
import com.toyota.consumer.service.PersistenceService;
import com.toyota.consumer.util.RateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class SimpleRateListener {

    private final PersistenceService persistenceService;
    private final RateParser rateParser;

    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumeRateMessages(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        log.info("Received batch of {} records from topic", records.size());
        
        // Group by Pipeline ID for intelligent processing
        Map<String, List<String>> pipelineBatches = records.stream()
            .collect(Collectors.groupingBy(
                ConsumerRecord::key, // Pipeline ID (BATCH_USDTRY_1749399209461)
                Collectors.mapping(ConsumerRecord::value, Collectors.toList())
            ));
        
        log.info("Processing {} pipeline batches with {} total messages", 
                pipelineBatches.size(), records.size());
        
        List<RateEntity> allEntitiesToPersist = new ArrayList<>();
        int totalUnparseable = 0;
        
        // Process each pipeline batch
        for (Map.Entry<String, List<String>> entry : pipelineBatches.entrySet()) {
            String pipelineId = entry.getKey();
            List<String> messages = entry.getValue();
            
            ProcessingResult result = processPipelineBatch(pipelineId, messages);
            allEntitiesToPersist.addAll(result.entitiesToPersist);
            totalUnparseable += result.unparseableCount;
        }

        // Bulk persistence
        if (!allEntitiesToPersist.isEmpty()) {
            try {
                List<RateEntity> persistedEntities = persistenceService.saveAllRates(allEntitiesToPersist);
                log.info("Successfully persisted {} entities from {} pipeline batches", 
                        persistedEntities.size(), pipelineBatches.size());
            } catch (Exception e) {
                log.error("Error persisting batch of {} entities from {} pipelines. Acknowledgment withheld for retry.",
                    allEntitiesToPersist.size(), pipelineBatches.size(), e);
                return;
            }
        } else {
            log.info("No processable messages in any pipeline batch to persist.");
        }

        acknowledgment.acknowledge();
        log.info("Batch acknowledged: {} pipeline batches, {} total messages, {} unparseable", 
                pipelineBatches.size(), records.size(), totalUnparseable);
    }
    
    private ProcessingResult processPipelineBatch(String pipelineId, List<String> messages) {
        List<RateEntity> entitiesToPersist = new ArrayList<>();
        int unparseableCount = 0;
        
        for (String message : messages) {
            try {
                Optional<RateEntity> entityOptional = rateParser.parseToEntity(message);
                if (entityOptional.isPresent()) {
                    RateEntity entity = entityOptional.get();
                    // Add pipeline metadata
                    entity.setPipelineId(pipelineId);
                    entity.setRateCategory(determineRateCategory(entity.getRateName()));
                    entitiesToPersist.add(entity);
                } else {
                    log.warn("Pipeline {}: Message could not be parsed: '{}'", pipelineId, message);
                    unparseableCount++;
                }
            } catch (Exception e) {
                log.error("Pipeline {}: Error parsing message: '{}'. Skipped.", pipelineId, message, e);
                unparseableCount++;
            }
        }
        
        log.debug("Pipeline {}: {} processable, {} unparseable messages", 
                pipelineId, entitiesToPersist.size(), unparseableCount);
        
        return new ProcessingResult(entitiesToPersist, unparseableCount);
    }
    
    private String determineRateCategory(String rateSymbol) {
        if (rateSymbol.contains("_AVG")) return "AVERAGE";
        if (rateSymbol.contains("_CROSS")) return "CROSS";
        if (rateSymbol.contains("-TCP") || rateSymbol.contains("-REST")) return "RAW";
        return "OTHER";
    }
    
    private static class ProcessingResult {
        final List<RateEntity> entitiesToPersist;
        final int unparseableCount;
        
        ProcessingResult(List<RateEntity> entitiesToPersist, int unparseableCount) {
            this.entitiesToPersist = entitiesToPersist;
            this.unparseableCount = unparseableCount;
        }
    }
}