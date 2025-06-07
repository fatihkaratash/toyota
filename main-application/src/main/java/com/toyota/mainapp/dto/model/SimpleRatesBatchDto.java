package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Batch wrapper for simple rates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleRatesBatchDto {
    
    private String batchId;
    private Long timestamp;
    private Integer rateCount;
    private List<SimpleRateDto> rates;
}
