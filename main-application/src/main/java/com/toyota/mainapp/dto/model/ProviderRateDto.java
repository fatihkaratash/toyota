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
    

    private String symbol;
    private String bid;
    private String ask;
    private String providerName;
    private String timestamp;
    
    /**
     * Zaman damgası (uzun tamsayı olarak)
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = String.valueOf(timestamp);
    }
}
