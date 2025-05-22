package com.toyota.mainapp.subscriber.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberConfigDto {
    private String name;
    private String type;
    private String implementationClass;
    private String baseUrl;
    private boolean enabled;
    private long pollIntervalMs;
    private List<String> subscribedSymbols;
    private Map<String, Object> connectionConfig;
    private Map<String, Object> additionalProperties;
}
