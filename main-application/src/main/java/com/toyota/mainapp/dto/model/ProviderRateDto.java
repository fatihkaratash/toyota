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
    
    private String symbol;              // ✅ USED: Symbol from provider
    private String bid;                 // ✅ USED: Bid price as string
    private String ask;                 // ✅ USED: Ask price as string  
    private String providerName;        // ✅ USED: Provider identification
    private Object timestamp;           // ✅ USED: Flexible timestamp handling

    /**
     * ✅ ACTIVELY USED: Long timestamp setter (TCP provider)
     * Usage: TcpRateSubscriber sets timestamp as long
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * ✅ ACTIVELY USED: String timestamp setter (REST provider)  
     * Usage: RestRateSubscriber sets ISO timestamp strings
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * ✅ ACTIVELY USED: Generic timestamp getter
     * Usage: RateMapper.safelyConvertTimestamp() handles Object → Long conversion
     */
    public Object getTimestamp() {
        return this.timestamp;
    }
}
