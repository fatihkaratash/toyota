package com.toyota.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "rate-provider")
public class ApplicationProperties {

    private Tcp tcp = new Tcp();
    private Rest rest = new Rest();
    private Map<String, RateConfig> initialRates;

    public static class RateConfig {
        private double base;
        private double volatility;

        public double getBase() {
            return base;
        }
        public void setBase(double base) {
            this.base = base;
        }
        public double getVolatility() {
            return volatility;
        }
        public void setVolatility(double volatility) {
            this.volatility = volatility;
        }
    }

    public static class Tcp {
        private int port;
        private List<String> availableRates;
        private long publishIntervalMs;

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public List<String> getAvailableRates() { return availableRates; }
        public void setAvailableRates(List<String> availableRates) { this.availableRates = availableRates; }
        public long getPublishIntervalMs() { return publishIntervalMs; }
        public void setPublishIntervalMs(long publishIntervalMs) { this.publishIntervalMs = publishIntervalMs; }
    }

    public static class Rest {
        private List<String> availableRates;
        private long generateIntervalMs;

        public List<String> getAvailableRates() { return availableRates; }
        public void setAvailableRates(List<String> availableRates) { this.availableRates = availableRates; }
        public long getGenerateIntervalMs() { return generateIntervalMs; }
        public void setGenerateIntervalMs(long generateIntervalMs) { this.generateIntervalMs = generateIntervalMs; }
    }

    public Tcp getTcp() { return tcp; }
    public void setTcp(Tcp tcp) { this.tcp = tcp; }
    public Rest getRest() { return rest; }
    public void setRest(Rest rest) { this.rest = rest; }
    public Map<String, RateConfig> getInitialRates() { return initialRates; }
    public void setInitialRates(Map<String, RateConfig> initialRates) { this.initialRates = initialRates; }
}
