package com.toyota.mainapp.kafka.publisher;

import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.RateMessageDto;
import com.toyota.mainapp.dto.RateType;
import com.toyota.mainapp.dto.payload.RatePayloadDto;
import com.toyota.mainapp.mapper.RateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tüm kur verilerini Kafka'ya merkezi olarak yayınlayan servis.
 * Hem JSON hem de basit metin formatındaki tüm mesajların tek noktadan yönetimini sağlar.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SequentialPublisher {

    private final KafkaTemplate<String, RateMessageDto> jsonKafkaTemplate;
    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final RateMapper rateMapper;
    
    // JSON formatı için konu adları
    @Value("${app.kafka.topic.raw-rates:financial-raw-rates}")
    private String rawRatesTopic;
    
    @Value("${app.kafka.topic.calculated-rates:financial-calculated-rates}")
    private String calculatedRatesTopic;
    
    // Metin formatı için konu adı
    @Value("${app.kafka.topic.simple-rates:financial-simple-rates}")
    private String simpleRatesTopic;

    // Basit metin formatı için tarih formatı
    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
    
    // Throttling için son gönderim zamanları
    private final Map<String, Long> lastSentTimestamps = new ConcurrentHashMap<>();
    private static final long MIN_SEND_INTERVAL_MS = 1000; // 1 saniye

    /**
     * Genel kur yayınlama metodu - Tüm kur tiplerini (RAW, CALCULATED, STATUS) işler
     */
    public void publishRate(BaseRateDto rate) {
        if (rate == null) {
            log.warn("Kur verisi boş, Kafka'ya yayınlanamıyor");
            return;
        }

        try {
            RateType rateType = rate.getRateType();
            if (rateType == null) {
                log.warn("Kur tipi belirtilmemiş, Kafka'ya yayınlanamıyor: {}", rate.getSymbol());
                return;
            }

            log.debug("Kur yayınlanıyor: {}, tipi: {}", rate.getSymbol(), rateType);
            
            switch (rateType) {
                case RAW:
                    publishRawRate(rate);
                    break;
                case CALCULATED:
                    publishCalculatedRate(rate);
                    break;
                case STATUS:
                    publishStatusRate(rate);
                    break;
                default:
                    log.warn("Desteklenmeyen kur tipi: {}, yayınlanamıyor", rateType);
            }
        } catch (Exception e) {
            log.error("Kuru yayınlarken hata: {} ({}) - {}", 
                    rate.getSymbol(), rate.getRateType(), e.getMessage(), e);
        }
    }

    /**
     * Ham kuru hem JSON hem de basit metin formatında Kafka'ya yayınlar
     */
    private void publishRawRate(BaseRateDto rawRate) {
        String providerSymbol = rawRate.getProviderName() + "_" + rawRate.getSymbol();
        log.debug("Ham kur yayınlanıyor: {}", providerSymbol);
        
        // 1. JSON formatında gönder
        RatePayloadDto payload = rateMapper.toRatePayloadDto(rawRate);
        sendJsonMessage(rawRatesTopic, providerSymbol, buildJsonMessage("RAW", payload));
        
        // 2. Basit metin formatında gönder (throttling ile)
        if (shouldSendRawRateToSimpleTopic(rawRate)) {
            String simpleKey = providerSymbol;
            long currentTime = System.currentTimeMillis();
            Long lastSentTime = lastSentTimestamps.get(simpleKey);
            
            if (lastSentTime == null || (currentTime - lastSentTime) >= MIN_SEND_INTERVAL_MS) {
                lastSentTimestamps.put(simpleKey, currentTime);
                String formattedMessage = formatRate(
                        providerSymbol,
                        rawRate.getBid(),
                        rawRate.getAsk(),
                        rawRate.getTimestamp());
                
                sendTextMessage(simpleRatesTopic, rawRate.getSymbol(), formattedMessage);
            }
        }
        
        log.debug("Ham kur başarıyla yayınlandı: {}", providerSymbol);
    }
    
    /**
     * Hesaplanmış kuru hem JSON hem de basit metin formatında Kafka'ya yayınlar
     */
    private void publishCalculatedRate(BaseRateDto calculatedRate) {
        String symbol = calculatedRate.getSymbol();
        log.debug("Hesaplanmış kur yayınlanıyor: {}", symbol);
        
        // 1. JSON formatında gönder
        RatePayloadDto payload = rateMapper.toRatePayloadDto(calculatedRate);
        sendJsonMessage(calculatedRatesTopic, symbol, buildJsonMessage("CALCULATED", payload));
        
        // 2. Basit metin formatında gönder (throttling ile)
        if (shouldSendCalculatedRateToSimpleTopic(calculatedRate)) {
            String simpleKey = "AVG_" + symbol;
            long currentTime = System.currentTimeMillis();
            Long lastSentTime = lastSentTimestamps.get(simpleKey);
            
            if (lastSentTime == null || (currentTime - lastSentTime) >= MIN_SEND_INTERVAL_MS) {
                lastSentTimestamps.put(simpleKey, currentTime);
                String formattedMessage = formatRate(
                        symbol,
                        calculatedRate.getBid(),
                        calculatedRate.getAsk(),
                        calculatedRate.getTimestamp());
                
                sendTextMessage(simpleRatesTopic, symbol, formattedMessage);
            }
        }
        
        log.debug("Hesaplanmış kur başarıyla yayınlandı: {}", symbol);
    }

    /**
     * Durum kur bilgisini Kafka'ya yayınlar
     */
    private void publishStatusRate(BaseRateDto statusRate) {
        String providerSymbol = statusRate.getProviderName() + "_" + statusRate.getSymbol();
        log.debug("Durum kuru yayınlanıyor: {}, durum: {}", providerSymbol, statusRate.getStatus());
        
        // Sadece JSON formatında gönder
        RatePayloadDto payload = rateMapper.toRatePayloadDto(statusRate);
        sendJsonMessage(rawRatesTopic, providerSymbol, buildJsonMessage("STATUS", payload));
        
        log.debug("Durum kuru başarıyla yayınlandı: {}", providerSymbol);
    }
    
    /**
     * Belirli bir ham kurun basit metin formatında gönderilip gönderilmeyeceğini belirler
     */
    private boolean shouldSendRawRateToSimpleTopic(BaseRateDto rawRate) {
        String providerName = rawRate.getProviderName();
        return "TCPProvider2".equals(providerName) || "RESTProvider1".equals(providerName);
    }
    
    /**
     * Belirli bir hesaplanmış kurun basit metin formatında gönderilip gönderilmeyeceğini belirler
     */
    private boolean shouldSendCalculatedRateToSimpleTopic(BaseRateDto calculatedRate) {
        String symbol = calculatedRate.getSymbol();
        return symbol.contains("AVG") || symbol.endsWith("_AVG");
    }
    
    /**
     * JSON formatında mesaj oluştur
     */
    private RateMessageDto buildJsonMessage(String rateType, RatePayloadDto payload) {
        return RateMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .messageTimestamp(System.currentTimeMillis())
                .rateType(rateType)
                .payload(payload)
                .build();
    }
    
    /**
     * Basit metin formatında mesaj oluştur
     */
    private String formatRate(String symbol, BigDecimal bid, BigDecimal ask, Long timestamp) {
        String isoTimestamp = timestamp == null 
                ? ISO_FORMATTER.format(Instant.now())
                : ISO_FORMATTER.format(Instant.ofEpochMilli(timestamp));
                
        return String.format("%s|%s|%s|%s", 
                symbol, 
                formatDecimal(bid), 
                formatDecimal(ask), 
                isoTimestamp);
    }
    
    /**
     * BigDecimal değerini metin formatına dönüştür
     */
    private String formatDecimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
    
    /**
     * JSON formatında Kafka mesajı gönder
     */
    private void sendJsonMessage(String topic, String key, RateMessageDto message) {
        CompletableFuture<SendResult<String, RateMessageDto>> future = 
                jsonKafkaTemplate.send(topic, key, message);
                
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("JSON mesajı başarıyla gönderildi: konu={}, anahtar={}", topic, key);
            } else {
                log.error("JSON mesajı gönderilemedi: konu={}, anahtar={}, hata={}", 
                        topic, key, ex.getMessage(), ex);
            }
        });
    }
    
    /**
     * Metin formatında Kafka mesajı gönder
     */
    private void sendTextMessage(String topic, String key, String message) {
        CompletableFuture<SendResult<String, String>> future = 
                stringKafkaTemplate.send(topic, key, message);
                
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Metin mesajı başarıyla gönderildi: konu={}, anahtar={}", topic, key);
            } else {
                log.error("Metin mesajı gönderilemedi: konu={}, anahtar={}, hata={}", 
                        topic, key, ex.getMessage(), ex);
            }
        });
    }
}