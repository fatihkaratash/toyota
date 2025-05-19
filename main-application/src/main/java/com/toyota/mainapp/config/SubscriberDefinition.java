package com.toyota.mainapp.config;

import java.util.List;
import java.util.Map;

public class SubscriberDefinition {
    private String type; // "tcp", "rest"
    private String name; // e.g., ProviderTCP1
    private String className; // Fully qualified name of the subscriber implementation class
    private String host; // For TCP
    private Integer port; // For TCP
    private String url; // For REST
    private Long pollIntervalMs; // For REST
    private List<String> subscribedSymbols;
    private Map<String, Object> additionalProperties; // For any other specific properties

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(Long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public List<String> getSubscribedSymbols() {
        return subscribedSymbols;
    }

    public void setSubscribedSymbols(List<String> subscribedSymbols) {
        this.subscribedSymbols = subscribedSymbols;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}
