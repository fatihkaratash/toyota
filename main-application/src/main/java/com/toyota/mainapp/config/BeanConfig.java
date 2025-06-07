package com.toyota.mainapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * ✅ FIXED: Bean configuration without circular dependency
 * Removed @DependsOn to allow proper Spring initialization order
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class BeanConfig {

    /**
     * ✅ PRIMARY: JSON processing ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        log.info("✅ ObjectMapper bean configured with JavaTimeModule");
        return mapper;
    }

    /**
     * ✅ TASK SCHEDULER: For scheduled operations
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("Scheduled-");
        scheduler.initialize();
        log.info("✅ TaskScheduler configured with 5 threads");
        return scheduler;
    }

    /**
     * ✅ PIPELINE EXECUTOR: RealTimeBatchProcessor dedicated executor
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
        log.info("✅ pipelineTaskExecutor configured: core=3, max=8, queue=15");
        return executor;
    }

    /**
     * ✅ SUBSCRIBER EXECUTOR: TCP/REST provider I/O operations
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
        log.info("✅ subscriberTaskExecutor configured: core=5, max=10, queue=25");
        return executor;
    }

    /**
     * ✅ WEB CLIENT: HTTP requests builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        log.info("✅ WebClient.Builder bean configured");
        return WebClient.builder();
    }

    /**
     * ✅ RETRY REGISTRY: Resilience4j retry mechanism
     */
    @Bean
    public RetryRegistry retryRegistry() {
        log.info("✅ RetryRegistry bean configured");
        return RetryRegistry.ofDefaults();
    }

    /**
     * ✅ CIRCUIT BREAKER: Resilience4j circuit breaker mechanism
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.info("✅ CircuitBreakerRegistry bean configured");
        return CircuitBreakerRegistry.ofDefaults();
    }
}
