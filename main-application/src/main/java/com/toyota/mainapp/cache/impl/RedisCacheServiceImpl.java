package com.toyota.mainapp.cache.impl;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class RedisCacheServiceImpl implements RateCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String RAW_RATE_PREFIX = "raw_rate:";
    private static final String CALC_RATE_PREFIX = "calc_rate:";
    private long defaultTtlSeconds;

    public RedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        
        // Load TTL from environment
        String envTtl = System.getenv("CACHE_TTL_SECONDS");
        if (envTtl != null && !envTtl.trim().isEmpty()) {
            try {
                this.defaultTtlSeconds = Long.parseLong(envTtl.trim());
                log.info("Cache TTL configured from environment: {} seconds", this.defaultTtlSeconds);
            } catch (NumberFormatException e) {
                log.warn("Invalid CACHE_TTL_SECONDS value: {}, using default: 86400", envTtl);
                this.defaultTtlSeconds = 86400;
            }
        } else {
            this.defaultTtlSeconds = 86400; // 24 hours default
        }
    }
    
    @Override
    public void cacheRate(String key, BaseRateDto rateDto) {
        if (key == null || rateDto == null) {
            log.warn("Null key or rate provided to cacheRate");
            return;
        }
        
        try {
            redisTemplate.opsForValue().set(key, rateDto, defaultTtlSeconds, TimeUnit.SECONDS);
            log.debug("Cached rate with key: {}", key);
        } catch (Exception e) {
            log.error("Error caching rate for key {}: {}", key, e.getMessage(), e);
        }
    }
    
    @Override
    public Optional<BaseRateDto> getRate(String key) {
        if (key == null) {
            return Optional.empty();
        }
        
        try {
            Object cachedValue = redisTemplate.opsForValue().get(key);
            if (cachedValue == null) {
                return Optional.empty();
            }
            
            if (cachedValue instanceof BaseRateDto) {
                return Optional.of((BaseRateDto) cachedValue);
            } else {
                log.warn("Retrieved non-BaseRateDto object from cache for key: {}", key);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error retrieving rate for key {}: {}", key, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public void cacheCalculatedRate(BaseRateDto calculatedRate) {
        if (calculatedRate == null || calculatedRate.getSymbol() == null) {
            log.warn("Null rate or symbol provided to cacheCalculatedRate");
            return;
        }
        
        calculatedRate.setRateType(RateType.CALCULATED);
        String key = CALC_RATE_PREFIX + calculatedRate.getSymbol();
        cacheRate(key, calculatedRate);
        log.info("Cached calculated rate: {} (bid={}, ask={})", 
                calculatedRate.getSymbol(), calculatedRate.getBid(), calculatedRate.getAsk());
    }
    
    @Override
    public Optional<BaseRateDto> getCalculatedRate(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        
        // If symbol already has prefix, use as is
        String key = symbol.startsWith(CALC_RATE_PREFIX) ? symbol : CALC_RATE_PREFIX + symbol;
        return getRate(key);
    }
    
    @Override
    public List<BaseRateDto> getAllRawRatesForSymbol(String symbol) {
        if (symbol == null) {
            return Collections.emptyList();
        }
        
        try {
            String pattern = RAW_RATE_PREFIX + "*" + symbol + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<BaseRateDto> rates = new ArrayList<>();
            for (String key : keys) {
                getRate(key).ifPresent(rates::add);
            }
            
            return rates;
        } catch (Exception e) {
            log.error("Error retrieving raw rates for symbol {}: {}", symbol, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public Map<String, BaseRateDto> getAllCalculatedRates() {
        try {
            String pattern = CALC_RATE_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyMap();
            }
            
            Map<String, BaseRateDto> calculatedRates = new HashMap<>();
            for (String key : keys) {
                getRate(key).ifPresent(rate -> {
                    // Use the symbol without prefix as the map key
                    String symbol = key.substring(CALC_RATE_PREFIX.length());
                    calculatedRates.put(symbol, rate);
                });
            }
            
            log.debug("Retrieved {} calculated rates from cache", calculatedRates.size());
            return calculatedRates;
        } catch (Exception e) {
            log.error("Error retrieving all calculated rates: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    public BaseRateDto getCachedRate(String key) {
    try {
        Object value = redisTemplate.opsForValue().get(key);
        
        // Better type checking and conversion
        if (value instanceof BaseRateDto) {
            return (BaseRateDto) value;
        } else if (value instanceof Map) {
            log.debug("Converting Map to BaseRateDto for key: {}", key);
            return objectMapper.convertValue(value, BaseRateDto.class);
        } else if (value != null) {
            log.warn("Retrieved non-BaseRateDto object from cache for key: {}. Type: {}", 
                    key, value.getClass().getName());
            return null;
        }
        return null;
    } catch (Exception e) {
        log.error("Error retrieving from cache for key: {}", key, e);
        return null;
    }
}
    @Override
    public void cacheRawRate(String key, BaseRateDto rawRate) {
        if (rawRate != null) {
            rawRate.setRateType(RateType.RAW);
            cacheRate(key, rawRate);
        }
    }
}