package com.toyota.mainapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import com.toyota.mainapp.calculator.RuleEngineService;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;

/**
 * Ana uygulama yapılandırma sınıfı
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AppConfig {

    private final ApplicationConfiguration appConfig;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final RuleEngineService ruleEngineService;

    @Value("${app.calculator.config.path:classpath:calculation-config.json}")
    private String calculationConfigPath;

    public AppConfig(ApplicationConfiguration appConfig,
                     ResourceLoader resourceLoader,
                     ObjectMapper objectMapper,
                     RuleEngineService ruleEngineService) {
        this.appConfig = appConfig;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.ruleEngineService = ruleEngineService;
    }

    /**
     * Uygulamanın JSON işlemleri için ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * TaskScheduler for scheduled tasks
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("Scheduled-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * ✅ RealTimeBatchProcessor için özel executor
     */
    @Bean(name = "pipelineTaskExecutor")
    public TaskExecutor pipelineTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(15);
        executor.setThreadNamePrefix("Pipeline-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Abone iş parçacıkları için görev çalıştırıcı - OPTIMIZED
     */
    @Bean(name = "subscriberTaskExecutor")
    public TaskExecutor subscriberTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("Subscriber-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Hesaplama işlemleri için görev çalıştırıcı - ENHANCED
     */
    @Bean(name = "calculationTaskExecutor")
    public TaskExecutor calculationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4); // Increased for real-time processing
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("Calculator-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * HTTP istekleri için WebClient oluşturucu
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Yeniden deneme mekanizması kaydı
     */
    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }

    /**
     * Devre kesici mekanizması kaydı
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    /**
     * ✅ SIMPLE: Load calculation config and populate ApplicationConfiguration
     */
    @PostConstruct
    public void loadCalculationRules() {
        log.info("Loading calculation rules from: {}", calculationConfigPath);
        
        try {
            // Load JSON file
            var resource = resourceLoader.getResource(calculationConfigPath);
            if (!resource.exists()) {
                log.error("Calculation config file not found: {}", calculationConfigPath);
                return;
            }

            String json = new String(FileCopyUtils.copyToByteArray(resource.getInputStream()), StandardCharsets.UTF_8);
            JsonNode configNode = objectMapper.readTree(json);
            
            // ✅ Parse rules directly to existing DTO
            if (configNode.has("rules")) {
                List<CalculationRuleDto> rules = objectMapper.convertValue(
                    configNode.get("rules"), 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CalculationRuleDto.class)
                );
                
                // ✅ Set to ApplicationConfiguration
                appConfig.setCalculationRules(rules);
                
                // ✅ Set to RuleEngineService
                ruleEngineService.setCalculationRules(rules);
                log.info("LOADED {} calculation rules", rules.size());
            }
            
            // ✅ Parse symbol providers (no aggregator initialization)
            if (configNode.has("symbolProviders")) {
                Map<String, List<String>> symbolProviders = objectMapper.convertValue(
                    configNode.get("symbolProviders"),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class).getRawClass())
                );
                
                // ✅ Set to ApplicationConfiguration only
                appConfig.setSymbolProvidersMap(symbolProviders);
                
                // ✅ REMOVED: twoWayWindowAggregator.initialize(symbolProviders);
                log.info("LOADED {} symbol provider mappings (no aggregator)", symbolProviders.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to load calculation configuration", e);
        }
    }
}
