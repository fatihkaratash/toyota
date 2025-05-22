package com.toyota.mainapp.aggregator;

import com.toyota.mainapp.dto.RateMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RawRateAggregatorListener {

    private final TwoWayWindowAggregator aggregator;

    @KafkaListener(
        topics = "${app.kafka.topic.raw-rates}",
        groupId = "rate-aggregator-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRawRate(RateMessageDto message, @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long kafkaTimestamp) {
        log.debug("Ham kur mesajı alındı: {}, Kafka timestamp: {}", message, kafkaTimestamp);
        
        try {
            aggregator.accept(message);
        } catch (Exception e) {
            log.error("Ham kur mesajı işlenirken hata oluştu: {}", message, e);
            // Burada bir yeniden deneme mekanizması veya dead letter queue uygulanabilir
        }
    }
}
