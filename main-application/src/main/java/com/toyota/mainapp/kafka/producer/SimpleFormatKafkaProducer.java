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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimpleFormatKafkaProducer {

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    
    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
    
    @Value("${app.kafka.topic.simple-rates:financial-simple-rates}")
    private String simpleRatesTopic;

    // Her kur çifti + sağlayıcı kombinasyonu için son gönderim zamanını tut
    private final Map<String, Long> lastSentTimestamps = new ConcurrentHashMap<>();
    
    // Gönderimler arası minimum süre (milisaniye) - 1 saniye
    private static final long MIN_SEND_INTERVAL_MS = 1000;

    /**
     * Ham kur verisini Kafka'ya basit metin formatında gönderir
     * Sadece belirli sağlayıcılardan gelen kurlar için ve belirli aralıklarla gönderim yapılır
     */
    public void sendRawRate(RawRateDto rawRate) {
        if (rawRate == null) {
            log.warn("Ham kur verisi boş, basit format Kafka'ya gönderilemiyor");
            return;
        }

        String baseSymbol = rawRate.getSymbol();
        String providerName = rawRate.getProviderName();
        
        String key = providerName + "_" + baseSymbol;
        long currentTime = System.currentTimeMillis();
        
        // Provider'ın gönderim yapabileceğini kontrol et
        if (shouldSendRateFromProvider(baseSymbol, providerName)) {
            // Son gönderimden bu yana yeterli süre geçmiş mi kontrol et
            Long lastSentTime = lastSentTimestamps.get(key);
            
            if (lastSentTime == null || (currentTime - lastSentTime) >= MIN_SEND_INTERVAL_MS) {
                // Minimum süre geçmiş, gönderimi yap ve zamanı güncelle
                lastSentTimestamps.put(key, currentTime);
                
                // Sağlayıcı adını ve sembolü birleştir -> PF1_USDTRY veya PF2_EURUSD gibi
                String formattedSymbol = providerName + "_" + baseSymbol;
                
                String message = formatRate(
                        formattedSymbol,
                        rawRate.getBid(),
                        rawRate.getAsk(),
                        rawRate.getTimestamp());
                        
                sendMessage(simpleRatesTopic, baseSymbol, message);
                log.debug("Kafka'ya gönderildi (throttled): {} için {}", baseSymbol, providerName);
            } else {
                log.trace("Kafka gönderimi atlandı (throttled): {} için {} (min süre dolmadı)", 
                        baseSymbol, providerName);
            }
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
            return true; // TCPProvider2'den gelen tüm kurları ilet (frekans kontrollü)
        }
        
        // REST kur sağlayıcısından seçilecek kaynaklar
        if (providerName.equals("RESTProvider1")) {
            return true; // RESTProvider1'den gelen tüm kurları ilet (frekans kontrollü)
        }
        
        // Diğer tüm ham kurları filtreleme
        return false;
    }

    /**
     * Hesaplanmış kur verisini Kafka'ya basit metin formatında gönderir
     * Sadece belirli hesaplanmış kurlar için ve belirli aralıklarla gönderim yapılır
     */
    public void sendCalculatedRate(CalculatedRateDto calculatedRate) {
        if (calculatedRate == null) {
            log.warn("Hesaplanmış kur verisi boş, basit format Kafka'ya gönderilemiyor");
            return;
        }
        
        // Sadece AVG ile ilgili hesaplanmış kurları gönder
        if (shouldSendCalculatedRate(calculatedRate.getSymbol())) {
            String key = "AVG_" + calculatedRate.getSymbol();
            long currentTime = System.currentTimeMillis();
            
            // Son gönderimden bu yana yeterli süre geçmiş mi kontrol et
            Long lastSentTime = lastSentTimestamps.get(key);
            
            if (lastSentTime == null || (currentTime - lastSentTime) >= MIN_SEND_INTERVAL_MS) {
                // Minimum süre geçmiş, gönderimi yap ve zamanı güncelle
                lastSentTimestamps.put(key, currentTime);
                
                String message = formatRate(
                        calculatedRate.getSymbol(),
                        calculatedRate.getBid(),
                        calculatedRate.getAsk(),
                        calculatedRate.getTimestamp());
                        
                sendMessage(simpleRatesTopic, calculatedRate.getSymbol(), message);
                log.debug("Kafka'ya gönderildi (throttled): AVG için {}", calculatedRate.getSymbol());
            } else {
                log.trace("Kafka gönderimi atlandı (throttled): AVG için {} (min süre dolmadı)", 
                        calculatedRate.getSymbol());
            }
        }
    }

    /**
     * Belirli bir hesaplanmış kurun Kafka'ya gönderilip gönderilmeyeceğini belirler
     */
    private boolean shouldSendCalculatedRate(String symbol) {
        // AVG içeren veya _AVG ile biten hesaplanmış kurları gönder
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