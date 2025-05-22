package com.toyota.mainapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for subscribers loaded from application.properties.
 * Uses Spring's property binding to map configuration to Java objects.
 */
@Configuration
@ConfigurationProperties(prefix = "subscriber")
public class SubscriberProperties {
    
    private TcpSubscriberProperties tcp = new TcpSubscriberProperties();
    private RestSubscriberProperties rest = new RestSubscriberProperties();

    public TcpSubscriberProperties getTcp() {
        return tcp;
    }

    public void setTcp(TcpSubscriberProperties tcp) {
        this.tcp = tcp;
    }

    public RestSubscriberProperties getRest() {
        return rest;
    }

    public void setRest(RestSubscriberProperties rest) {
        this.rest = rest;
    }

    /**
     * TCP Subscriber specific properties.
     */
    public static class TcpSubscriberProperties {
        private int connectionTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
        private int maxReconnectDelaySeconds = 60;
        private int initialReconnectDelaySeconds = 5;
        private int defaultPort = 8081;

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getMaxReconnectDelaySeconds() {
            return maxReconnectDelaySeconds;
        }

        public void setMaxReconnectDelaySeconds(int maxReconnectDelaySeconds) {
            this.maxReconnectDelaySeconds = maxReconnectDelaySeconds;
        }

        public int getInitialReconnectDelaySeconds() {
            return initialReconnectDelaySeconds;
        }

        public void setInitialReconnectDelaySeconds(int initialReconnectDelaySeconds) {
            this.initialReconnectDelaySeconds = initialReconnectDelaySeconds;
        }

        public int getDefaultPort() {
            return defaultPort;
        }

        public void setDefaultPort(int defaultPort) {
            this.defaultPort = defaultPort;
        }
    }

    /**
     * REST Subscriber specific properties.
     */
    public static class RestSubscriberProperties {
        private int connectionTimeoutMs = 3000;
        private int readTimeoutMs = 3000;
        private int circuitFailureThreshold = 3;
        private int circuitSuccessThreshold = 2;
        private int circuitTimeoutSeconds = 60;
        private long defaultPollIntervalMs = 5000;

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getCircuitFailureThreshold() {
            return circuitFailureThreshold;
        }

        public void setCircuitFailureThreshold(int circuitFailureThreshold) {
            this.circuitFailureThreshold = circuitFailureThreshold;
        }

        public int getCircuitSuccessThreshold() {
            return circuitSuccessThreshold;
        }

        public void setCircuitSuccessThreshold(int circuitSuccessThreshold) {
            this.circuitSuccessThreshold = circuitSuccessThreshold;
        }

        public int getCircuitTimeoutSeconds() {
            return circuitTimeoutSeconds;
        }

        public void setCircuitTimeoutSeconds(int circuitTimeoutSeconds) {
            this.circuitTimeoutSeconds = circuitTimeoutSeconds;
        }

        public long getDefaultPollIntervalMs() {
            return defaultPollIntervalMs;
        }

        public void setDefaultPollIntervalMs(long defaultPollIntervalMs) {
            this.defaultPollIntervalMs = defaultPollIntervalMs;
        }
    }
}
