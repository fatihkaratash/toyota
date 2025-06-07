package com.toyota.mainapp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ✅ FIXED: Configuration loading with resolved circular dependency
 * ObjectMapper injected via setter to prevent circular reference
 */
@Component("applicationProperties")
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
@Slf4j
public class ApplicationProperties {

    private final ResourceLoader resourceLoader;
    private final ReentrantLock configLock = new ReentrantLock();

    // ✅ CRITICAL FIX: ObjectMapper injected via setter, NOT constructor
    private ObjectMapper objectMapper;

    @Value("${app.calculator.config.path:classpath:calculation-config.json}")
    private String calculationConfigPath;

    // ✅ NEW: Subscribers configuration
    private SubscribersConfig subscribers = new SubscribersConfig();

    // Configuration state
    private List<CalculationRuleDto> calculationRules = new ArrayList<>();
    private Map<String, List<String>> symbolProvidersMap;
    private boolean configurationReady = false;

    /**
     * ✅ FIXED: Constructor without ObjectMapper dependency
     */
    public ApplicationProperties(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        log.debug("✅ ApplicationProperties created without ObjectMapper dependency");
    }

    /**
     * ✅ CRITICAL: Setter injection for ObjectMapper
     */
    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.debug("✅ ObjectMapper injected into ApplicationProperties");
    }

    /**
     * ✅ ENHANCED: PostConstruct with dependency validation
     */
    @PostConstruct
    public void loadCalculationConfiguration() {
        log.info("🔧 Loading calculation configuration from: {}", calculationConfigPath);
        
        // ✅ CRITICAL: Validate ObjectMapper is available
        if (objectMapper == null) {
            log.error("❌ CRITICAL: ObjectMapper not injected - using fallback");
            this.objectMapper = new ObjectMapper(); // Fallback to prevent complete failure
        }
        
        configLock.lock();
        try {
            var resource = resourceLoader.getResource(calculationConfigPath);
            if (!resource.exists()) {
                log.warn("⚠️ Calculation config file not found: {}. Using empty configuration.", calculationConfigPath);
                this.configurationReady = true; // Allow startup with empty config
                return;
            }

            String json = new String(FileCopyUtils.copyToByteArray(resource.getInputStream()), StandardCharsets.UTF_8);
            JsonNode configNode = objectMapper.readTree(json);
            
            // ✅ STANDARDIZED: Try "calculationRules" first, fallback to "rules"
            JsonNode rulesNode = configNode.path("calculationRules");
            if (rulesNode.isMissingNode()) {
                rulesNode = configNode.path("rules");
                log.warn("⚠️ Using legacy 'rules' key - please migrate to 'calculationRules'");
            }
            
            if (!rulesNode.isMissingNode()) {
                this.calculationRules = objectMapper.convertValue(
                    rulesNode, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CalculationRuleDto.class)
                );
                log.info("✅ Loaded {} calculation rules", calculationRules.size());
            }
            
            // Load symbol providers mapping
            JsonNode providersNode = configNode.path("symbolProviders");
            if (!providersNode.isMissingNode()) {
                this.symbolProvidersMap = objectMapper.convertValue(
                    providersNode,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, List.class)
                );
                log.info("✅ Loaded {} symbol provider mappings", symbolProvidersMap != null ? symbolProvidersMap.size() : 0);
            }
            
            this.configurationReady = true;
            log.info("🎉 Configuration loading completed successfully");
            
        } catch (Exception e) {
            log.error("❌ Failed to load calculation configuration", e);
            this.configurationReady = true; // Allow startup even with config failure
        } finally {
            configLock.unlock();
        }
    }

    /**
     * ✅ THREAD-SAFE: Configuration ready check
     */
    public boolean isConfigurationReady() {
        configLock.lock();
        try {
            return configurationReady;
        } finally {
            configLock.unlock();
        }
    }

    /**
     * Get calculation rules (thread-safe)
     */
    public List<CalculationRuleDto> getCalculationRules() {
        configLock.lock();
        try {
            return new ArrayList<>(calculationRules);
        } finally {
            configLock.unlock();
        }
    }

    /**
     * ✅ NEW: Getter for subscribers config path
     */
    public String getSubscribersConfigPath() {
        return subscribers.getConfigPath();
    }

    /**
     * ✅ NEW: Subscribers configuration inner class
     */
    @Getter
    @Setter
    public static class SubscribersConfig {
        private String configPath = "classpath:subscribers.json"; // Default value
    }

    @Data
    public static class PipelineConfig {
        
        @Data
        public static class ErrorHandling {
            /**
             * Continue pipeline execution when individual stages fail
             */
            private boolean continueOnStageFailure = true;
            
            /**
             * Maximum stage errors before stopping pipeline
             */
            private int maxStageErrors = 3;
            
            /**
             * Publish partial snapshots even with stage errors
             */
            private boolean publishPartialSnapshots = true;
            
            /**
             * Log stage errors as warnings instead of errors
             */
            private boolean treatStageErrorsAsWarnings = false;
        }
        
        private ErrorHandling errorHandling = new ErrorHandling();
        
        /**
         * Pipeline execution timeout in milliseconds
         */
        private long executionTimeoutMs = 5000L;
        
        /**
         * Maximum snapshot size before splitting
         */
        private int maxSnapshotSize = 100;
    }
    
    private PipelineConfig pipeline = new PipelineConfig();
    
    public PipelineConfig getPipeline() {
        return pipeline;
    }
}
