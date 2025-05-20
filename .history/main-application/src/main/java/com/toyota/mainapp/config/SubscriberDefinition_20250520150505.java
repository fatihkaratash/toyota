package com.toyota.mainapp.config;

import java.util.List;
import java.util.Map;

/**
 * Definition of a subscriber.
 */
public class SubscriberDefinition {
    
    private String type;
    private String name;
    private String className;
    private String host;
    private Integer port;
    private String url;
    private Long pollIntervalMs;
    private List<String> subscribedSymbols;
    private Map<String, Object> additionalProperties;
    
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
    
    @Override
    public String toString() {
        return "SubscriberDefinition{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", className='" + className + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", url='" + url + '\'' +
                ", pollIntervalMs=" + pollIntervalMs +
                ", subscribedSymbols=" + subscribedSymbols +
                ", additionalProperties=" + additionalProperties +
                '}';
    }
}
