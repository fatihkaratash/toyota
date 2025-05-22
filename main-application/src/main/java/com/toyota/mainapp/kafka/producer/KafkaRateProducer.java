package com.toyota.mainapp.kafka.producer;

import com.toyota.mainapp.dto.RateMessageDto;
import com.toyota.mainapp.dto.payload.CalculatedRatePayloadDto;
import com.toyota.mainapp.dto.payload.RawRatePayloadDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kur verilerini Kafka'ya yayınlayan servis
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaRateProducer {

    private final KafkaTemplate<String, RateMessageDto> kafkaTemplate;
    
    @Value("${app.kafka.topic.raw-rates}")
    private String rawRatesTopic;
    
    @Value("${app.kafka.topic.calculated-rates}")
    private String calculatedRatesTopic;

    /**
     * Ham kur verisini Kafka'ya gönderir
     */
    public void sendRawRate(RawRatePayloadDto rawRatePayload) {
        if (rawRatePayload == null) {
            log.warn("Ham kur verisi boş, Kafka'ya gönderilemiyor");
            return;
        }

        String key = rawRatePayload.getProviderName() + "_" + rawRatePayload.getSymbol();
        
        RateMessageDto message = buildMessage("RAW", rawRatePayload);
        sendMessage(rawRatesTopic, key, message);
    }

    /**
     * Hesaplanmış kur verisini Kafka'ya gönderir
     */
    public void sendCalculatedRate(CalculatedRatePayloadDto calculatedRatePayload) {
        if (calculatedRatePayload == null) {
            log.warn("Hesaplanmış kur verisi boş, Kafka'ya gönderilemiyor");
            return;
        }

        String key = calculatedRatePayload.getSymbol();
        
        RateMessageDto message = buildMessage("CALCULATED", calculatedRatePayload);
        sendMessage(calculatedRatesTopic, key, message);
    }
    
    /**
     * RateMessageDto oluşturur
     */
    private RateMessageDto buildMessage(String rateType, Object payload) {
        return RateMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .messageTimestamp(System.currentTimeMillis())
                .rateType(rateType)
                .payload(payload)
                .build();
    }
    
    /**
     * Mesajı Kafka'ya gönderir ve sonucu asenkron olarak işler
     */
    private void sendMessage(String topic, String key, RateMessageDto message) {
        CompletableFuture<SendResult<String, RateMessageDto>> future = 
                kafkaTemplate.send(topic, key, message);
                
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Kafka mesajı başarıyla gönderildi: konu={}, anahtar={}", 
                        topic, key);
                log.debug("Mesaj detayları: {}", message);
            } else {
                log.error("Kafka mesajı gönderilemedi: konu={}, anahtar={}, hata={}", 
                        topic, key, ex.getMessage(), ex);
            }
        });
    }
}
