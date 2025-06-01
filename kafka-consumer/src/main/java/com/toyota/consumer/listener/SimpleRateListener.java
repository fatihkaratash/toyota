package com.toyota.consumer.listener;

import com.toyota.consumer.model.RateEntity;
import com.toyota.consumer.service.PersistenceService;
import com.toyota.consumer.util.RateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        containerFactory = "kafkaListenerContainerFactory" // Ensure this factory is configured for batch listening
    )
    public void consumeRateMessages(List<String> messages, Acknowledgment acknowledgment) {
        log.info("Received batch of {} messages from topic '{}'.", messages.size(), "${app.kafka.topic.simple-rates}");
        List<RateEntity> entitiesToPersist = new ArrayList<>();
        List<String> unparseableMessages = new ArrayList<>();

        for (String message : messages) {
            try {
                Optional<RateEntity> entityOptional = rateParser.parseToEntity(message);
                if (entityOptional.isPresent()) {
                    entitiesToPersist.add(entityOptional.get());
                } else {
                    log.warn("Message could not be parsed and will be skipped: '{}'", message);
                    unparseableMessages.add(message);
                }
            } catch (Exception e) {
                log.error("Error parsing message: '{}'. It will be skipped.", message, e);
                unparseableMessages.add(message);
            }
        }

        if (!entitiesToPersist.isEmpty()) {
            try {
                List<RateEntity> persistedEntities = persistenceService.saveAllRates(entitiesToPersist);
                log.info("Successfully persisted {} entities out of {} processable messages.", persistedEntities.size(), entitiesToPersist.size());
                // Detaylı kur bazlı loglama kaldırıldı. Sadece başarılı kayıt sayısı loglanıyor.
            } catch (Exception e) {
                log.error("Error persisting batch of {} entities. Message sample: [{}]. Acknowledgment will be withheld to allow for retry.",
                    entitiesToPersist.size(),
                    entitiesToPersist.stream().limit(3).map(RateEntity::getRateName).collect(Collectors.joining(", ")),
                    e);
           
                return;
            }
        } else {
            log.info("No processable messages in the batch to persist.");
        }

     
        acknowledgment.acknowledge();
        log.info("Batch of {} messages acknowledged. {} unparseable messages were skipped.", messages.size(), unparseableMessages.size());
    }
}