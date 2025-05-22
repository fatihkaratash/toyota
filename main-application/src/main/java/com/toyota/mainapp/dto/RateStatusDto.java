package com.toyota.mainapp.dto;

import lombok.Data;

/**
 * Data Transfer Object for rate status
 */
@Data
public class RateStatusDto {
    private String symbol;
    private String providerName;
    private RateStatusEnum status;
    private String statusMessage;
    private long timestamp;
    
    /**
     * Enum for different rate statuses
     */
    public enum RateStatusEnum {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        INVALID,
        PENDING,
        ERROR
    }
}
