package com.toyota.mainapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.toyota.mainapp.dto.model.BaseRateDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Toyota Financial Data Platform - Redis Configuration
 * 
 * Configures Redis connection factory and specialized templates for
 * raw and calculated rate caching. Optimizes serialization and
 * pipeline performance for high-throughput financial data operations.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Configuration
@Slf4j
@DependsOn("applicationProperties")  // Ensure config loads first
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Check environment variables first, then fallback to properties
        String envRedisHost = System.getenv("REDIS_HOST");
        String envRedisPort = System.getenv("REDIS_PORT");
        
        String finalHost = (envRedisHost != null && !envRedisHost.trim().isEmpty()) ? envRedisHost : redisHost;
        int finalPort = redisPort;
        
        if (envRedisPort != null && !envRedisPort.trim().isEmpty()) {
            try {
                finalPort = Integer.parseInt(envRedisPort.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid REDIS_PORT environment value: {}, using default: {}", envRedisPort, redisPort);
            }
        }
        
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(finalHost);
        redisConfig.setPort(finalPort);
        
        if (!redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }
        
        redisConfig.setDatabase(redisDatabase);
        
        log.info("✅ Redis connection configured: {}:{}, database: {}", finalHost, finalPort, redisDatabase);
        return new LettuceConnectionFactory(redisConfig);
    }

    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        log.info("✅ Redis ObjectMapper configured with default typing");
        return mapper;
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Configure serializers
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use Jackson2JsonRedisSerializer with type information
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = redisObjectMapper();
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
        
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        
        template.afterPropertiesSet();
        log.info("✅ Generic RedisTemplate configured");
        return template;
    }

    @Bean("rawRateRedisTemplate")
    public RedisTemplate<String, BaseRateDto> rawRateRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, BaseRateDto> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure key serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Configure specialized value serializer for BaseRateDto
        Jackson2JsonRedisSerializer<BaseRateDto> serializer = new Jackson2JsonRedisSerializer<>(BaseRateDto.class);
        serializer.setObjectMapper(redisObjectMapper());
        
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        log.info("✅ Raw rate RedisTemplate configured for BaseRateDto");
        return template;
    }

    @Bean("calculatedRateRedisTemplate")
    public RedisTemplate<String, BaseRateDto> calculatedRateRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, BaseRateDto> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure key serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Configure specialized value serializer for BaseRateDto
        Jackson2JsonRedisSerializer<BaseRateDto> serializer = new Jackson2JsonRedisSerializer<>(BaseRateDto.class);
        serializer.setObjectMapper(redisObjectMapper());
        
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        log.info("✅ Calculated rate RedisTemplate configured for BaseRateDto");
        return template;
    }

    @Bean("rateRedisTemplate")
    public RedisTemplate<String, BaseRateDto> rateRedisTemplate(RedisConnectionFactory connectionFactory) {

        return rawRateRedisTemplate(connectionFactory);
    }
}
