package com.toyota.mainapp.cache;

import com.toyota.mainapp.dto.model.BaseRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ✅ MODERNIZED: Type-safe Redis cache service with specialized templates
 * Optimized for BaseRateDto operations and pipeline performance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateCacheService {

    @Qualifier("rawRateRedisTemplate")
    private final RedisTemplate<String, BaseRateDto> rawRateRedisTemplate;

    @Qualifier("calculatedRateRedisTemplate") 
    private final RedisTemplate<String, BaseRateDto> calculatedRateRedisTemplate;

    @Value("${app.cache.raw-rate.ttl-seconds:15}")
    private int rawRateTtlSeconds;

    @Value("${app.cache.calculated-rate.ttl-seconds:10}")
    private int calculatedRateTtlSeconds;

    @Value("${app.cache.key-prefix:toyota_rates}")
    private String keyPrefix;

    /**
     * ✅ TYPE-SAFE: Cache raw rate with specialized template
     */
    public void cacheRawRate(BaseRateDto rate) {
        if (rate == null || rate.getSymbol() == null || rate.getProviderName() == null) {
            log.warn("❌ Invalid rate data for caching: {}", rate);
            return;
        }

        String key = buildRawRateKey(rate.getSymbol(), rate.getProviderName());
        
        try {
            rawRateRedisTemplate.opsForValue().set(key, rate, rawRateTtlSeconds, TimeUnit.SECONDS);
            log.debug("✅ Raw rate cached: key={}, ttl={}s", key, rawRateTtlSeconds);
        } catch (Exception e) {
            log.error("❌ Failed to cache raw rate: key={}", key, e);
        }
    }

    /**
     * ✅ TYPE-SAFE: Cache calculated rate with specialized template
     */
    public void cacheCalculatedRate(BaseRateDto rate) {
        if (rate == null || rate.getSymbol() == null) {
            log.warn("❌ Invalid calculated rate data for caching: {}", rate);
            return;
        }

        String key = buildCalculatedRateKey(rate.getSymbol());
        
        try {
            calculatedRateRedisTemplate.opsForValue().set(key, rate, calculatedRateTtlSeconds, TimeUnit.SECONDS);
            log.debug("✅ Calculated rate cached: key={}, ttl={}s", key, calculatedRateTtlSeconds);
        } catch (Exception e) {
            log.error("❌ Failed to cache calculated rate: key={}", key, e);
        }
    }

    /**
     * ✅ BATCH OPERATION: Get multiple raw rates efficiently with MGET
     */
    public Map<String, BaseRateDto> getRawRatesBatch(String symbol, List<String> providerNames) {
        if (symbol == null || providerNames == null || providerNames.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = providerNames.stream()
                .map(provider -> buildRawRateKey(symbol, provider))
                .collect(Collectors.toList());

        try {
            List<BaseRateDto> rates = rawRateRedisTemplate.opsForValue().multiGet(keys);
            Map<String, BaseRateDto> result = new HashMap<>();

            for (int i = 0; i < keys.size() && i < rates.size(); i++) {
                BaseRateDto rate = rates.get(i);
                if (rate != null) {
                    result.put(providerNames.get(i), rate);
                }
            }

            log.debug("✅ Batch raw rates retrieved: symbol={}, found={}/{}", 
                    symbol, result.size(), providerNames.size());
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to get raw rates batch: symbol={}, providers={}", symbol, providerNames, e);
            return Collections.emptyMap();
        }
    }

    /**
     * ✅ BATCH OPERATION: Get multiple calculated rates efficiently
     */
    public Map<String, BaseRateDto> getCalculatedRatesBatch(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = symbols.stream()
                .map(this::buildCalculatedRateKey)
                .collect(Collectors.toList());

        try {
            List<BaseRateDto> rates = calculatedRateRedisTemplate.opsForValue().multiGet(keys);
            Map<String, BaseRateDto> result = new HashMap<>();

            for (int i = 0; i < keys.size() && i < rates.size(); i++) {
                BaseRateDto rate = rates.get(i);
                if (rate != null) {
                    result.put(symbols.get(i), rate);
                }
            }

            log.debug("✅ Batch calculated rates retrieved: found={}/{}", result.size(), symbols.size());
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to get calculated rates batch: symbols={}", symbols, e);
            return Collections.emptyMap();
        }
    }

    /**
     * ✅ SINGLE OPERATIONS: Backward compatibility
     */
    public BaseRateDto getRawRate(String symbol, String providerName) {
        String key = buildRawRateKey(symbol, providerName);
        try {
            BaseRateDto rate = rawRateRedisTemplate.opsForValue().get(key);
            log.debug("Raw rate retrieved: key={}, found={}", key, rate != null);
            return rate;
        } catch (Exception e) {
            log.error("❌ Failed to get raw rate: key={}", key, e);
            return null;
        }
    }

    public BaseRateDto getCalculatedRate(String symbol) {
        String key = buildCalculatedRateKey(symbol);
        try {
            BaseRateDto rate = calculatedRateRedisTemplate.opsForValue().get(key);
            log.debug("Calculated rate retrieved: key={}, found={}", key, rate != null);
            return rate;
        } catch (Exception e) {
            log.error("❌ Failed to get calculated rate: key={}", key, e);
            return null;
        }
    }

    /**
     * ✅ KEY BUILDERS: Consistent key generation
     */
    private String buildRawRateKey(String symbol, String providerName) {
        return String.format("%s:raw_rate:%s:%s", keyPrefix, symbol, providerName);
    }

    private String buildCalculatedRateKey(String symbol) {
        return String.format("%s:calc_rate:%s", keyPrefix, symbol);
    }

    /**
     * ✅ UTILITY: Check rate freshness
     */
    public boolean isRateFresh(String symbol, String providerName, long maxAgeMs) {
        BaseRateDto rate = getRawRate(symbol, providerName);
        if (rate == null || rate.getTimestamp() == null) {
            return false;
        }
        
        long age = System.currentTimeMillis() - rate.getTimestamp();
        return age <= maxAgeMs;
    }

    /**
     * ✅ MONITORING: Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rawRateTtlSeconds", rawRateTtlSeconds);
        stats.put("calculatedRateTtlSeconds", calculatedRateTtlSeconds);
        stats.put("keyPrefix", keyPrefix);
        return stats;
    }
}