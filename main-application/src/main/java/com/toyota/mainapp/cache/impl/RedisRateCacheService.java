package com.toyota.mainapp.cache.impl;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RedisRateCacheService implements RateCacheService {

    private static final String RAW_RATE_PREFIX = "raw_rate:";
    private static final String CALCULATED_RATE_PREFIX = "calc_rate:";

    private final RedisTemplate<String, BaseRateDto> rateRedisTemplate;
    private final long rawRateTtlSeconds;
    private final long calculatedRateTtlSeconds;

    public RedisRateCacheService(
            @Qualifier("rateRedisTemplate") RedisTemplate<String, BaseRateDto> rateRedisTemplate,
            @Value("${app.cache.raw-rate.ttl-seconds:3600}") long rawRateTtlSeconds,
            @Value("${app.cache.calculated-rate.ttl-seconds:3600}") long calculatedRateTtlSeconds) {
        this.rateRedisTemplate = rateRedisTemplate;
        this.rawRateTtlSeconds = rawRateTtlSeconds;
        this.calculatedRateTtlSeconds = calculatedRateTtlSeconds;
    }

    private String buildRawRateCacheKey(String key) {
        return RAW_RATE_PREFIX + key;
    }

    private String buildCalculatedRateCacheKey(String symbol) {
        return CALCULATED_RATE_PREFIX + symbol;
    }

    @Override
    public void cacheRate(String key, BaseRateDto rateDto) {
        if (rateDto == null) {
            log.warn("Cannot cache null BaseRateDto with key: {}", key);
            return;
        }
        
        try {
            // Determine key prefix based on rate type
            String cacheKey;
            long ttl;
            
            if (rateDto.getRateType() == RateType.CALCULATED) {
                cacheKey = buildCalculatedRateCacheKey(key);
                ttl = calculatedRateTtlSeconds;
            } else {
                cacheKey = buildRawRateCacheKey(key);
                ttl = rawRateTtlSeconds;
            }
                
            log.debug("Caching BaseRateDto with key: {}, type: {}, object: {}", 
                     cacheKey, rateDto.getRateType(), rateDto);
                     
            rateRedisTemplate.opsForValue().set(cacheKey, rateDto, ttl, TimeUnit.SECONDS);
            log.debug("Rate cached successfully: {} with key: {}", rateDto.getRateType(), cacheKey);
        } catch (Exception e) {
            log.error("Error caching rate with key {}: {}", key, e.getMessage(), e);
        }
    }

    @Override
    public Optional<BaseRateDto> getRate(String key) {
        // Try both raw and calculated prefixes if key doesn't have prefix
        if (!key.startsWith(RAW_RATE_PREFIX) && !key.startsWith(CALCULATED_RATE_PREFIX)) {
            // Try raw first
            Optional<BaseRateDto> rawResult = getRateWithExactKey(buildRawRateCacheKey(key));
            if (rawResult.isPresent()) {
                return rawResult;
            }
            // Then try calculated
            return getRateWithExactKey(buildCalculatedRateCacheKey(key));
        } else {
            // Key already has prefix
            return getRateWithExactKey(key);
        }
    }
    
    private Optional<BaseRateDto> getRateWithExactKey(String exactKey) {
        try {
            BaseRateDto rate = rateRedisTemplate.opsForValue().get(exactKey);
            if (rate != null) {
                log.debug("Retrieved rate from Redis with key {}: type={}", 
                         exactKey, rate.getRateType());
                return Optional.of(rate);
            } else {
                log.debug("Rate not found in cache for key: {}", exactKey);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error retrieving rate with key {}: {}", exactKey, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheRawRate(String key, BaseRateDto rawRate) {
        if (rawRate != null) {
            rawRate.setRateType(RateType.RAW);
            cacheRate(key, rawRate);
        }
    }

    @Override
    public void cacheCalculatedRate(BaseRateDto calculatedRate) {
        if (calculatedRate == null || calculatedRate.getSymbol() == null) {
            log.warn("Cannot cache null calculated rate or rate with null symbol");
            return;
        }
        calculatedRate.setRateType(RateType.CALCULATED);
        cacheRate(calculatedRate.getSymbol(), calculatedRate);
    }

    @Override
    public Optional<BaseRateDto> getCalculatedRate(String symbol) {
        if (symbol == null) {
            log.warn("Cannot get calculated rate for null symbol");
            return Optional.empty();
        }
        String cacheKey = buildCalculatedRateCacheKey(symbol);
        return getRate(cacheKey);
    }

    @Override
    public List<BaseRateDto> getAllRawRatesForSymbol(String baseSymbol) {
        try {
            // Use pattern matching to find all keys for the symbol
            String pattern = RAW_RATE_PREFIX + "*_" + baseSymbol;
            
            // Note: This is a simplified implementation and might not be efficient for large datasets
            // A real implementation would use Redis SCAN command or specific data structures
            
            List<BaseRateDto> results = new ArrayList<>();
            
            // Get all keys matching pattern - implementation depends on Redis client
            // For example, with Spring Data Redis:
            Set<String> keys = rateRedisTemplate.keys(pattern);
            if (keys != null) {
                for (String key : keys) {
                    Optional<BaseRateDto> rate = getRateWithExactKey(key);
                    rate.ifPresent(results::add);
                }
            }
            
            log.debug("Found {} raw rates for symbol {}", results.size(), baseSymbol);
            return results;
        } catch (Exception e) {
            log.error("Error getting all raw rates for symbol {}: {}", baseSymbol, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
