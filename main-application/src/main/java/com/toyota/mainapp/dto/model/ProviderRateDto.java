package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ham sağlayıcı verisini temsil eden DTO - SIMPLE FORMAT
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
    private Object timestamp; // ✅ Object olarak - hem String hem Long kabul eder
    
    /**
     * Zaman damgası (uzun tamsayı olarak) - backward compatibility
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * String timestamp setter
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Timestamp getter - her format için
     */
    public Object getTimestamp() {
        return this.timestamp;
    }
}
