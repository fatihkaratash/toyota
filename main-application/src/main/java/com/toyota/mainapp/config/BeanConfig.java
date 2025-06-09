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
 * Toyota Financial Data Platform - Bean Configuration
 * 
 * Central configuration for application beans including ObjectMapper,
 * task executors, schedulers, and resilience components. Provides
 * optimized thread pools for pipeline and subscriber operations.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class BeanConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        log.info("✅ ObjectMapper bean configured with JavaTimeModule");
        return mapper;
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("Scheduled-");
        scheduler.initialize();
        log.info("✅ TaskScheduler configured with 5 threads");
        return scheduler;
    }

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

    @Bean
    public WebClient.Builder webClientBuilder() {
        log.info("✅ WebClient.Builder bean configured");
        return WebClient.builder();
    }

    @Bean
    public RetryRegistry retryRegistry() {
        log.info("✅ RetryRegistry bean configured");
        return RetryRegistry.ofDefaults();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.info("✅ CircuitBreakerRegistry bean configured");
        return CircuitBreakerRegistry.ofDefaults();
    }
}
