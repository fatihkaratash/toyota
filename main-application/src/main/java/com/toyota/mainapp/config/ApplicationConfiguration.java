package com.toyota.mainapp.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.dto.config.CalculationRuleDto;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;

/**
 * UNIFIED APPLICATION CONFIGURATION
 * Tüm configuration tek merkezde - modular access
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
@Slf4j
public class ApplicationConfiguration {

    // Symbol Configuration
    private SymbolConfig symbol = new SymbolConfig();

    // Aggregator Configuration
    private AggregatorConfig aggregator = new AggregatorConfig();

    // Cross Rate Configuration
    private CrossRateConfig crossRate = new CrossRateConfig();

    // Provider Configuration
    private ProviderConfig provider = new ProviderConfig();

    // Kafka Configuration
    private KafkaConfig kafka = new KafkaConfig();

    // Cache Configuration
    private CacheConfig cache = new CacheConfig();

    // ✅ YENİ: Configuration state tracking
    private boolean configurationLoaded = false;
    private final Object configLock = new Object();
    
    // ✅ YENİ: Loaded configuration data
    private List<CalculationRuleDto> calculationRules = new ArrayList<>();
    private Map<String, List<String>> symbolProvidersMap = new HashMap<>();

    @Data
    public static class SymbolConfig {
        private String format = "STANDARD"; // 6-char uppercase
        private String calculatedSuffix = "_AVG";
        private String crossSuffix = "_CROSS";
        private String calcSuffix = "_CALC";
        private List<String> validCurrencies = List.of("USD", "EUR", "GBP", "TRY");
    }

    @Data
    public static class AggregatorConfig {
        private long windowTimeMs = 5000;
        private long maxTimeSkewMs = 2000;
        private boolean enableTimeSkewCheck = true;
        private Map<String, List<String>> symbolProviders = new HashMap<>();
        
        // ✅ YENİ: Getter/Setter for backward compatibility
        public Map<String, List<String>> getSymbolProviders() {
            return symbolProviders;
        }
        
        public void setSymbolProviders(Map<String, List<String>> symbolProviders) {
            this.symbolProviders = symbolProviders;
        }
    }

    @Data
    public static class CrossRateConfig {
        private String baseCurrency = "USD";
        private Map<String, List<String>> enabledPairs = new HashMap<>();
        private String calculationStrategy = "MULTIPLY_DIVIDE";
        private boolean enabled = true;

        // BUSINESS LOGIC METHODS - RateCalculatorService için
        public List<String> getDependencies(String crossRateSymbol) {
            return enabledPairs.getOrDefault(crossRateSymbol, List.of());
        }

        public boolean isPairEnabled(String crossRateSymbol) {
            return enabled && enabledPairs.containsKey(crossRateSymbol);
        }
    }

    @Data
    public static class ProviderConfig {
        private List<String> simpleTopicEnabled = List.of();
        private Map<String, ProviderSettings> settings = new HashMap<>();

        @Data
        public static class ProviderSettings {
            private int maxRetryAttempts = 3;
            private long timeoutMs = 5000;
            private boolean circuitBreakerEnabled = true;
            private String authentication = "none";
        }
    }

    @Data
    public static class KafkaConfig {
        private long throttleIntervalMs = 1000;
        private TopicConfig topics = new TopicConfig();

        @Data
        public static class TopicConfig {
            private String rawRates = "financial-raw-rates";
            private String calculatedRates = "financial-calculated-rates";
            private String simpleRates = "financial-simple-rates";
            private String crossRates = "financial-cross-rates";
        }
    }

    @Data
    public static class CacheConfig {
        private long rawRateTtlMinutes = 5;
        private long calculatedRateTtlMinutes = 10;
        private long crossRateTtlMinutes = 15;
        private boolean cleanupEnabled = true;
        private String keyPrefix = "toyota_rates";
    }

    /**
     * Cross rate için dependency'leri al - RateCalculatorService için
     */
    public List<String> getCrossRateDependencies(String crossRateSymbol) {
        return crossRate.getDependencies(crossRateSymbol);
    }

    /**
     * Provider simple topic'e gönderebilir mi?
     * DEPRECATED: Artık tüm provider'lar simple topic'e gönderebilir
     */
    @Deprecated
    public boolean canSendToSimpleTopic(String providerName) {
        // Backward compatibility için true döner
        return true;
    }

    /**
     * Cross rate enabled mi?
     */
    public boolean isCrossRateEnabled(String symbol) {
        return crossRate.isPairEnabled(symbol);
    }

    /**
     * Symbol providers map al - CalculationConfigLoader için
     */
    public Map<String, List<String>> getSymbolProvidersMap() {
        return symbolProvidersMap;
    }

    /**
     * Symbol providers map set et - CalculationConfigLoader için
     */
    public void setSymbolProvidersMap(Map<String, List<String>> symbolProviders) {
        this.symbolProvidersMap = symbolProviders;
    }

    /**
     * ✅ YENİ: Get calculation rules
     */
    public List<CalculationRuleDto> getCalculationRules() {
        return calculationRules;
    }

    /**
     * ✅ CRITICAL: Configuration loading method - ORDERED
     */
    @PostConstruct
    public void initializeConfiguration() {
        synchronized (configLock) {
            if (!configurationLoaded) {
                log.info("Initializing ApplicationConfiguration...");
                loadCalculationConfiguration();
                configurationLoaded = true;
                log.info("ApplicationConfiguration initialized successfully");
                // ✅ Notify waiting threads
                configLock.notifyAll();
            }
        }
    }

    private void loadCalculationConfiguration() {
        // Load from calculation-config.json
        try {
            ObjectMapper mapper = new ObjectMapper();
            Resource resource = new ClassPathResource("calculation-config.json");
            JsonNode rootNode = mapper.readTree(resource.getInputStream());

            // Load calculation rules
            JsonNode rulesNode = rootNode.path("calculationRules");
            if (rulesNode.isArray()) {
                List<CalculationRuleDto> rules = mapper.convertValue(
                    rulesNode, new TypeReference<List<CalculationRuleDto>>() {});
                
                this.calculationRules = rules;
                log.info("Loaded {} calculation rules", rules.size());
            }

            // Load symbol providers
            JsonNode symbolProvidersNode = rootNode.path("symbolProviders");
            if (symbolProvidersNode.isObject()) {
                Map<String, List<String>> providers = new HashMap<>();
                symbolProvidersNode.fields().forEachRemaining(entry -> {
                    String symbol = entry.getKey();
                    List<String> providerList = new ArrayList<>();
                    entry.getValue().forEach(node -> providerList.add(node.asText()));
                    providers.put(symbol, providerList);
                });
                this.symbolProvidersMap = providers;
                this.aggregator.setSymbolProviders(providers); // ✅ Set in aggregator too
                log.info("Loaded {} symbol provider mappings", providers.size());
            }

            // Load cross rate dependencies
            JsonNode crossRateNode = rootNode.path("crossRateDependencies");
            if (crossRateNode.isObject()) {
                Map<String, List<String>> crossDeps = new HashMap<>();
                crossRateNode.fields().forEachRemaining(entry -> {
                    String symbol = entry.getKey();
                    List<String> depList = new ArrayList<>();
                    entry.getValue().forEach(node -> depList.add(node.asText()));
                    crossDeps.put(symbol, depList);
                });
                this.crossRate.setEnabledPairs(crossDeps);
                log.info("Loaded {} cross rate dependencies", crossDeps.size());
            }

        } catch (Exception e) {
            log.error("Failed to load calculation configuration", e);
            this.calculationRules = Collections.emptyList();
            this.symbolProvidersMap = Collections.emptyMap();
        }
    }

    public boolean isConfigurationReady() {
        return configurationLoaded;
    }

    public void waitForConfiguration() {
        synchronized (configLock) {
            while (!configurationLoaded) {
                try {
                    configLock.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}