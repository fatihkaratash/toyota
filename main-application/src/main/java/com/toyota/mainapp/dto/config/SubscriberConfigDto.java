package com.toyota.mainapp.dto.config; // MODIFIED package

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberConfigDto {
    private String name;
    private String type;
    private String implementationClass;
    private boolean enabled;
    private Map<String, Object> connectionConfig;
    private Map<String, Object> additionalProperties;
}