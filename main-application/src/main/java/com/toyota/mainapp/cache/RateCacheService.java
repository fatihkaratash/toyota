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
 * ✅ OPTIMIZED: Redis cache service with safe batch operations
 * Removed KEYS command usage and improved consistency in key formats
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
     * ✅ CORE: Cache raw rate
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
     * ✅ CORE: Cache calculated rate
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
     * ✅ IMPROVED: Get raw rates for symbol from specific providers using MGET
     */
    public Map<String, BaseRateDto> getRawRatesForSymbol(String symbol, List<String> providerNames) {
        if (symbol == null || providerNames == null || providerNames.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // Generate keys for the specific symbol and providers
            List<String> keys = providerNames.stream()
                    .map(provider -> buildRawRateKey(symbol, provider))
                    .collect(Collectors.toList());

            // Use MGET for efficient batch retrieval
            List<BaseRateDto> rates = rawRateRedisTemplate.opsForValue().multiGet(keys);
            Map<String, BaseRateDto> result = new HashMap<>();

            if (rates != null) {
                for (int i = 0; i < rates.size(); i++) {
                    BaseRateDto rate = rates.get(i);
                    if (rate != null && i < providerNames.size()) {
                        result.put(providerNames.get(i), rate);
                    }
                }
            }

            log.debug("✅ Raw rates retrieved: symbol={}, found={}/{}", 
                    symbol, result.size(), providerNames.size());
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to get raw rates: symbol={}", symbol, e);
            return Collections.emptyMap();
        }
    }

    /**
     * ✅ IMPROVED: Get calculated rates in batch using MGET
     */
    public Map<String, BaseRateDto> getCalculatedRates(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // Generate standard keys for all symbols
            List<String> keys = symbols.stream()
                    .map(this::buildCalculatedRateKey)
                    .collect(Collectors.toList());

            // Use MGET for efficient batch retrieval
            List<BaseRateDto> rates = calculatedRateRedisTemplate.opsForValue().multiGet(keys);
            Map<String, BaseRateDto> result = new HashMap<>();

            if (rates != null) {
                for (int i = 0; i < symbols.size() && i < rates.size(); i++) {
                    BaseRateDto rate = rates.get(i);
                    if (rate != null) {
                        result.put(symbols.get(i), rate);
                    }
                }
            }

            log.debug("✅ Calculated rates batch retrieved: found={}/{}", result.size(), symbols.size());
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to get calculated rates in batch", e);
            return Collections.emptyMap();
        }
    }

    /**
     * ✅ SIMPLIFIED: Get latest calculated rate with standardized key
     */
    public BaseRateDto getLatestCalculatedRate(String symbol) {
        if (symbol == null) {
            return null;
        }

        try {
            String key = buildCalculatedRateKey(symbol);
            BaseRateDto rate = calculatedRateRedisTemplate.opsForValue().get(key);
            
            if (rate != null) {
                log.debug("✅ Retrieved calculated rate: {}", symbol);
                return rate;
            } else {
                log.debug("Calculated rate not found: {}", symbol);
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Error retrieving calculated rate: {}", symbol, e);
            return null;
        }
    }

    /**
     * ✅ ALIAS: Get calculated rate (same as getLatestCalculatedRate for consistency)
     */
    public BaseRateDto getCalculatedRate(String symbol) {
        return getLatestCalculatedRate(symbol);
    }

    /**
     * ✅ KEY BUILDERS: Consistent key generation
     */
    private String buildRawRateKey(String symbol, String providerName) {
        return String.format("%s:raw:%s:%s", keyPrefix, symbol, providerName);
    }

    private String buildCalculatedRateKey(String symbol) {
        return String.format("%s:calc:%s", keyPrefix, symbol);
    }

    /**
     * ✅ MONITORING: Simple cache statistics
     */
    public Map<String, Object> getCacheStats() {
        return Map.of(
            "rawRateTtlSeconds", rawRateTtlSeconds,
            "calculatedRateTtlSeconds", calculatedRateTtlSeconds,
            "keyPrefix", keyPrefix
        );
    }
}