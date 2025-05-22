package com.toyota.mainapp.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.RawRateDto;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.Executor;

/**
 * Ana uygulama yapılandırma sınıfı
 */
@Configuration
@EnableAsync
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
          mapper.activateDefaultTyping(
        mapper.getPolymorphicTypeValidator(), 
        ObjectMapper.DefaultTyping.NON_FINAL, 
        JsonTypeInfo.As.PROPERTY);
    return mapper;
    }

    /**
     * Ham kurlar için Redis şablonu
     
    @Bean
    @Qualifier("rawRateRedisTemplate")
    public RedisTemplate<String, RawRateDto> rawRateRedisTemplate(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, RawRateDto> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    
    // Use Jackson2JsonRedisSerializer instead of GenericJackson2JsonRedisSerializer
   GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);
    
    return template;
    }

    /**
     * Hesaplanmış kurlar için Redis şablonu
     
    @Bean
    @Qualifier("calculatedRateRedisTemplate")
    public RedisTemplate<String, CalculatedRateDto> calculatedRateRedisTemplate(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper) {
         RedisTemplate<String, CalculatedRateDto> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);
    
    return template;
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
