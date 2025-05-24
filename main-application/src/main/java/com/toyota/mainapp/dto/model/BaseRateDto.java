package com.toyota.mainapp.dto.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Base DTO for all rate data types
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseRateDto {
    
    /**
     * Rate type (RAW, CALCULATED, STATUS)
     */
    private RateType rateType;
    
    /**
     * Currency symbol (e.g., "USD/TRY")
     */
    private String symbol;
    
    /**
     * Bid price
     */
    private BigDecimal bid;
    
    /**
     * Ask price
     */
    private BigDecimal ask;
    
    /**
     * Provider name
     */
    private String providerName;
    
    /**
     * Timestamp from provider (epoch in milliseconds)
     */
    private Long timestamp;
    
    /**
     * Time received in system (epoch in milliseconds)
     */
    private Long receivedAt;
    
    /**
     * Time validated (epoch in milliseconds)
     */
    private Long validatedAt;
    
    /**
     * For CALCULATED rates: source rates used for calculation
     */
    @lombok.Builder.Default // Keep @Builder.Default if SuperBuilder doesn't initialize it
    private List<InputRateInfo> calculationInputs = new ArrayList<>();
    
    /**
     * For CALCULATED rates: the strategy used
     */
    private String calculatedByStrategy;
    
    /**
     * For STATUS rates: the current status
     */
    private RateStatusEnum status;
    
    /**
     * For STATUS rates: status message
     */
    private String statusMessage;
    
    /**
     * Status enumeration for rate status
     */
    public enum RateStatusEnum {
        ACTIVE,
        PENDING,
        ERROR
    }
}