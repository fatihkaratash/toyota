package com.toyota.mainapp.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.RawRateDto;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis connection configuration
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    /**
     * Creates a Redis connection factory
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        
        if (!redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }
        
        redisConfig.setDatabase(redisDatabase);
        
        log.info("Redis bağlantısı yapılandırılıyor: {}:{}, database: {}", redisHost, redisPort, redisDatabase);
        return new LettuceConnectionFactory(redisConfig);
    }

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Enable default typing to handle deserialization correctly
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        log.info("Redis ObjectMapper configured with default typing enabled");
        return mapper;
    }
    
    @Bean
    @Qualifier("rawRateRedisTemplate")
    public RedisTemplate<String, RawRateDto> rawRateRedisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        RedisTemplate<String, RawRateDto> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure key serializer
        template.setKeySerializer(new StringRedisSerializer());
        
        // Configure value serializer with proper type information
        Jackson2JsonRedisSerializer<RawRateDto> serializer = new Jackson2JsonRedisSerializer<>(RawRateDto.class);
        serializer.setObjectMapper(redisObjectMapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        log.info("RawRateDto RedisTemplate configured with Jackson2JsonRedisSerializer");
        return template;
    }

    @Bean
     @Qualifier("calculatedRateRedisTemplate")
    public RedisTemplate<String, CalculatedRateDto> calculatedRateRedisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        RedisTemplate<String, CalculatedRateDto> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Configure key serializer
        template.setKeySerializer(new StringRedisSerializer());
        
        // Configure value serializer with proper type information
        Jackson2JsonRedisSerializer<CalculatedRateDto> serializer = new Jackson2JsonRedisSerializer<>(CalculatedRateDto.class);
        serializer.setObjectMapper(redisObjectMapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        log.info("CalculatedRateDto RedisTemplate configured with Jackson2JsonRedisSerializer");
        return template;
    }
}
