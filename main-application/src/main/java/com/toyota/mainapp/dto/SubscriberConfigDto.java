package com.toyota.mainapp.dto;

import lombok.Data;

/**
 * Data Transfer Object for subscriber configuration
 */
@Data
public class SubscriberConfigDto {
    private String name;
    private String baseUrl;
    private String subscriberType;
    private long pollIntervalMs;
    private String apiKey;
    private String apiSecret;
    private String symbols;
    private boolean enabled;
}
