package com.toyota.mainapp.dto.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ham kur verisinin Kafka yük formatı
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawRatePayloadDto {
    
    /**
     * Veri sağlayıcı adı
     */
    private String providerName;
    
    /**
     * Kur sembol adı
     */
    private String symbol;
    
    /**
     * Alış fiyatı
     */
    private BigDecimal bid;
    
    /**
     * Satış fiyatı
     */
    private BigDecimal ask;
    
    /**
     * Sağlayıcıdan gelen zaman damgası
     */
    private Long timestamp;
    
    /**
     * Ham veri sistemde işlenme zamanı
     */
    private Long sourceReceivedAt;
    
    /**
     * Doğrulama zamanı
     */
    private Long sourceValidatedAt;
    
    /**
     * Olay tipi - sürekli "RAW_RATE"
     */
    private String eventType;
    
    /**
     * Olayın Kafka'ya gönderilme zamanı
     */
    private Long eventTime;
}
