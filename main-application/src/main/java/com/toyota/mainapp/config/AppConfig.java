package com.toyota.mainapp.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.task.TaskExecutor;

/**
 * Ana uygulama yapılandırma sınıfı
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AppConfig {

    /**
     * Uygulamanın JSON işlemleri için ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Enable default typing to preserve type information
      //  mapper.activateDefaultTyping(
        //    mapper.getPolymorphicTypeValidator(), 
          //  ObjectMapper.DefaultTyping.NON_FINAL, 
            //JsonTypeInfo.As.PROPERTY);
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
     * Abone iş parçacıkları için görev çalıştırıcı
     */
    @Bean(name = "subscriberTaskExecutor")
    public TaskExecutor subscriberTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("Subscriber-");
        executor.initialize();
        return executor;
    }

    /**
     * Hesaplama işlemleri için görev çalıştırıcı
     */
    @Bean(name = "calculationTaskExecutor")
    public TaskExecutor calculationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("Calculator-");
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
}
