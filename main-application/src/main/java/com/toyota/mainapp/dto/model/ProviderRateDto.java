package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ham sağlayıcı verisini temsil eden DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRateDto {
    
    /**
     * Kur sembol adı 
     */
    private String symbol;
    
    /**
     * Alış fiyatı (metin olarak)
     */
    private String bid;
    
    /**
     * Satış fiyatı (metin olarak)
     */
    private String ask;
    
    /**
     * Veri sağlayıcı adı
     */
    private String providerName;
    
    /**
     * Zaman damgası (metin olarak)
     */
    private String timestamp;
    
    /**
     * Zaman damgası (uzun tamsayı olarak)
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = String.valueOf(timestamp);
    }
}
