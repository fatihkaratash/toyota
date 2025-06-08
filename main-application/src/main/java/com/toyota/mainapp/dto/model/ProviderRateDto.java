package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ✅ ACTIVELY USED: Provider data input DTO
 * Used by: RestRateSubscriber, TcpRateSubscriber, MainCoordinatorService, RateMapper
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
    private Object timestamp;          


    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public Object getTimestamp() {
        return this.timestamp;
    }
}
