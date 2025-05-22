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
    
    // @Data annotation from Lombok will generate all getters and setters
    // But if you don't want to use Lombok, here are the manual methods:
    
    /*
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getSubscriberType() {
        return subscriberType;
    }
    
    public void setSubscriberType(String subscriberType) {
        this.subscriberType = subscriberType;
    }
    
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }
    
    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getApiSecret() {
        return apiSecret;
    }
    
    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }
    
    public String getSymbols() {
        return symbols;
    }
    
    public void setSymbols(String symbols) {
        this.symbols = symbols;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    */
}
