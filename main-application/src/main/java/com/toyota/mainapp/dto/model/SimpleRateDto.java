package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * Simple rate DTO for batch processing and external systems
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleRateDto {
    
    private String symbol;
    private BigDecimal bid;
    private BigDecimal ask;
    private Long timestamp;
    private String providerName;
    private String rateType;
}
