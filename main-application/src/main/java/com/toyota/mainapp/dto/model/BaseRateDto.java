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
 * Toyota Financial Data Platform - Base Rate Data Transfer Object
 * 
 * Central data structure for all rate types within the platform.
 * Supports raw provider rates, calculated averages, cross rates,
 * and status tracking with comprehensive metadata and audit information.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseRateDto {
    
    private RateType rateType;
    private String symbol;
    private BigDecimal bid;
    private BigDecimal ask;
    private String providerName;
    private Long timestamp;
    private Long receivedAt;
    private Long validatedAt;
    private String calculationType;

    @lombok.Builder.Default 
    private List<InputRateInfo> calculationInputs = new ArrayList<>();
    
    private String calculatedByStrategy;
    private RateStatusEnum status;
    private String statusMessage;

    public enum RateStatusEnum {
        ACTIVE,
        PENDING,
        ERROR
    }

    private Long lastCalculationTimestamp;
    public boolean isCalculatedRate() {
        return RateType.CALCULATED.equals(this.rateType);
    }

    public boolean isRawRate() {
        return RateType.RAW.equals(this.rateType);
    }
}