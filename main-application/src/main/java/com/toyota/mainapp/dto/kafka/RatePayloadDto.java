package com.toyota.mainapp.dto.kafka; // MODIFIED package

import com.toyota.mainapp.dto.model.BaseRateDto; // Correct import
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
    
    private String eventType;
    private Long eventTime;
    private Long sourceReceivedAt;
    private Long sourceValidatedAt;
    private Long rateTimestamp;
}