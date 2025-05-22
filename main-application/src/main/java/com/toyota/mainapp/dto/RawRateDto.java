package com.toyota.mainapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Doğrulanmış ham kur verilerini temsil eden DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawRateDto {
    
    /**
     * Kur sembol adı (örn: "USD/TRY")
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
     * Veri sağlayıcı adı
     */
    private String providerName;
    
    /**
     * Sağlayıcıdan gelen zaman damgası (milisaniye cinsinden epoch)
     */
    private Long timestamp;
    
    /**
     * Sistemde işlenme zamanı (milisaniye cinsinden epoch)
     */
    private Long receivedAt;
    
    /**
     * Doğrulama zamanı (milisaniye cinsinden epoch)
     */
    private Long validatedAt;
}
