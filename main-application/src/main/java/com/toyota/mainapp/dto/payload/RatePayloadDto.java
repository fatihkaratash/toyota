package com.toyota.mainapp.dto.payload;

import com.toyota.mainapp.dto.BaseRateDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Unified DTO for sending all rate types to Kafka
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RatePayloadDto extends BaseRateDto {
    
    /**
     * Event type for Kafka
     */
    private String eventType;
    
    /**
     * Time the event was sent to Kafka
     */
    private Long eventTime;
    
    /**
     * For RAW rates: system time when the rate was received
     */
    private Long sourceReceivedAt;
    
    /**
     * For RAW rates: system time when the rate was validated
     */
    private Long sourceValidatedAt;
    
    /**
     * For CALCULATED rates: timestamp of the rate calculation
     */
    private Long rateTimestamp;
}
