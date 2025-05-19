package com.toyota.mainapp.cache.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration class for cache-related beans.
 */
@Configuration
public class CacheConfig {

    @Value("${spring.redis.host:redis}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${app.cache.ttl.raw-rates:3600}")  // Default: 1 hour
    private long rawRatesTtlSeconds;

    @Value("${app.cache.ttl.calculated-rates:1800}") // Default: 30 minutes
    private long calculatedRatesTtlSeconds;

    /**
     * @return Redis connection factory using the configured host and port
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(redisConfig);
    }

    /**
     * @param connectionFactory the Redis connection factory
     * @return Redis template configured for general operations with JSON serialization
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * @return The configured time-to-live for raw rates in seconds
     */
    @Bean
    public long rawRatesTtlSeconds() {
        return rawRatesTtlSeconds;
    }

    /**
     * @return The configured time-to-live for calculated rates in seconds
     */
    @Bean
    public long calculatedRatesTtlSeconds() {
        return calculatedRatesTtlSeconds;
    }
}
