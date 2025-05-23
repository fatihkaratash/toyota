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
@RequiredArgsConstructor
@Slf4j
public class SimpleRateListener {

    private final PersistenceService persistenceService;
    private final RateParser rateParser;

    @KafkaListener(
            topics = "${app.kafka.topic.simple-rates:financial-simple-rates}",
            groupId = "${spring.kafka.consumer.group-id:simple-rate-group}"
    )
    public void consumeRateMessage(String message, Acknowledgment acknowledgment) {
        log.debug("Received simple rate message: {}", message);

        try {
            Optional<RateEntity> rateEntityOpt = rateParser.parseToEntity(message);

            if (rateEntityOpt.isPresent()) {
                persistenceService.saveRate(rateEntityOpt.get());
                log.info("Successfully processed and saved rate: {}", rateEntityOpt.get().getRateName());
                acknowledgment.acknowledge();
            } else {
                log.warn("Could not parse rate message, skipping and acknowledging: {}", message);
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            log.error("Error processing rate message: {}. Will not acknowledge.", message, e);
        }
    }
}
