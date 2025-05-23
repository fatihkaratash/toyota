package com.toyota.mainapp.kafka.producer;

import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.RawRateDto;
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
import java.util.concurrent.CompletableFuture;

/**
 * Kur verilerini Kafka'ya basit metin formatında yayınlayan servis.
 * Format: SEMBOL|BID|ASK|TIMESTAMP (pipe-delimited)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimpleFormatKafkaProducer {

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    
    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
    
    @Value("${app.kafka.topic.simple-rates:financial-simple-rates}")
    private String simpleRatesTopic;

    /**
 * Ham kur verisini Kafka'ya basit metin formatında gönderir
 * Sadece belirli sağlayıcılardan gelen kurlar için gönderim yapılır
 */
public void sendRawRate(RawRateDto rawRate) {
    if (rawRate == null) {
        log.warn("Ham kur verisi boş, basit format Kafka'ya gönderilemiyor");
        return;
    }

    String baseSymbol = rawRate.getSymbol();
    String providerName = rawRate.getProviderName();
    
    // Her kur çifti için sadece belirli sağlayıcılardan gelen verileri ilet
    if (shouldSendRateFromProvider(baseSymbol, providerName)) {
        String message = formatRate(
                rawRate.getSymbol(),
                rawRate.getBid(),
                rawRate.getAsk(),
                rawRate.getTimestamp());
                
        sendMessage(simpleRatesTopic, rawRate.getSymbol(), message);
        log.debug("Kafka'ya gönderildi (filtered): {} için {}", baseSymbol, providerName);
    } else {
        log.debug("Kafka gönderimi atlandı (filtered out): {} için {}", baseSymbol, providerName);
    }
}

/**
 * Belirli bir kur çifti için belirli bir sağlayıcıdan gelen verinin 
 * Kafka'ya gönderilip gönderilmeyeceğini belirler
 */
private boolean shouldSendRateFromProvider(String baseSymbol, String providerName) {
    // TCP kur sağlayıcısından seçilecek kaynaklar
    if (providerName.equals("TCPProvider2")) {
        return true; // TCPProvider2'den gelen tüm kurları ilet
    }
    
    // REST kur sağlayıcısından seçilecek kaynaklar
    if (providerName.equals("RESTProvider1")) {
        return true; // RESTProvider1'den gelen tüm kurları ilet
    }
    
    // Diğer tüm ham kurları filtreleme
    return false;
}
    /**
     * Ham kur verisini Kafka'ya basit metin formatında gönderir
     
    public void sendRawRate(RawRateDto rawRate) {
        if (rawRate == null) {
            log.warn("Ham kur verisi boş, basit format Kafka'ya gönderilemiyor");
            return;
        }
        
        String message = formatRate(
                rawRate.getProviderName() + "_" + rawRate.getSymbol(),
                rawRate.getBid(),
                rawRate.getAsk(),
                rawRate.getTimestamp());
                
        sendMessage(simpleRatesTopic, rawRate.getSymbol(), message);
    }


     * Hesaplanmış kur verisini Kafka'ya basit metin formatında gönderir
     
    public void sendCalculatedRate(CalculatedRateDto calculatedRate) {
        if (calculatedRate == null) {
            log.warn("Hesaplanmış kur verisi boş, basit format Kafka'ya gönderilemiyor");
            return;
        }
        
        String message = formatRate(
                calculatedRate.getSymbol(),
                calculatedRate.getBid(),
                calculatedRate.getAsk(),
                calculatedRate.getTimestamp());
                
        sendMessage(simpleRatesTopic, calculatedRate.getSymbol(), message);
    }
    
    
     * Kur verilerini formatla: SEMBOL|BID|ASK|TIMESTAMP
     */

     /**
 * Hesaplanmış kur verisini Kafka'ya basit metin formatında gönderir
 * Sadece belirli hesaplanmış kurlar için gönderim yapılır
 */
public void sendCalculatedRate(CalculatedRateDto calculatedRate) {
    if (calculatedRate == null) {
        log.warn("Hesaplanmış kur verisi boş, basit format Kafka'ya gönderilemiyor");
        return;
    }
    
    // Sadece AVG_ ile biten hesaplanmış kurları gönder
    if (shouldSendCalculatedRate(calculatedRate.getSymbol())) {
        String message = formatRate(
                calculatedRate.getSymbol(),
                calculatedRate.getBid(),
                calculatedRate.getAsk(),
                calculatedRate.getTimestamp());
                
        sendMessage(simpleRatesTopic, calculatedRate.getSymbol(), message);
    }
}

/**
 * Belirli bir hesaplanmış kurun Kafka'ya gönderilip gönderilmeyeceğini belirler
 */
private boolean shouldSendCalculatedRate(String symbol) {
    // Örneğin sadece AVG_ ile biten hesaplanmış kurları göndermek istiyorsak:
    return symbol.contains("AVG") || symbol.endsWith("_AVG");
}
    private String formatRate(String symbol, BigDecimal bid, BigDecimal ask, Long timestamp) {
        // İstenen yazı formatı: PF1_USDTRY|33.60|35.90|2024-12-16T16:07:15.504
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
     * Mesajı Kafka'ya gönder ve sonucu asenkron olarak işle
     */
    private void sendMessage(String topic, String key, String message) {
        CompletableFuture<SendResult<String, String>> future = 
                stringKafkaTemplate.send(topic, key, message);
                
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Basit format Kafka mesajı başarıyla gönderildi: konu={}, anahtar={}, içerik={}", 
                        topic, key, message);
            } else {
                log.error("Basit format Kafka mesajı gönderilemedi: konu={}, anahtar={}, içerik={}, hata={}", 
                        topic, key, message, ex.getMessage(), ex);
            }
        });
    }
}
