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

    @Value("${app.cache.key-prefix:}")
    private String keyPrefix;

    public void cacheRawRate(BaseRateDto rate) {
        if (rate == null || rate.getSymbol() == null || rate.getProviderName() == null) {
            return;
        }

        String key = buildRawRateKey(rate.getSymbol(), rate.getProviderName());
       try {
        // Mevcut değeri kontrol et
        BaseRateDto existingRate = rawRateRedisTemplate.opsForValue().get(key);
        if (existingRate != null && 
            Objects.equals(existingRate.getBid(), rate.getBid()) && 
            Objects.equals(existingRate.getAsk(), rate.getAsk())) {
            // Sadece TTL'i yenile, değeri tekrar yazma
            rawRateRedisTemplate.expire(key, rawRateTtlSeconds, TimeUnit.SECONDS);
            return;
        }
        
        // Değer farklı veya mevcut değilse, yeni değeri yaz
        rawRateRedisTemplate.opsForValue().set(key, rate, rawRateTtlSeconds, TimeUnit.SECONDS);
    } catch (Exception e) {
        log.error("Failed to cache raw rate: key={}", key, e);
    }
    }

    public void cacheCalculatedRate(BaseRateDto rate) {
        if (rate == null || rate.getSymbol() == null) {
            return;
        }

        String key = buildCalculatedRateKey(rate.getSymbol());
        
        try {
            calculatedRateRedisTemplate.opsForValue().set(key, rate, calculatedRateTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to cache calculated rate: key={}", key, e);
        }
    }

    public Map<String, BaseRateDto> getRawRatesForSymbol(String symbol, List<String> providerNames) {
        if (symbol == null || providerNames == null || providerNames.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<String> keys = providerNames.stream()
                    .map(provider -> buildRawRateKey(symbol, provider))
                    .collect(Collectors.toList());

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

            return result;

        } catch (Exception e) {
            log.error("Failed to get raw rates: symbol={}", symbol, e);
            return Collections.emptyMap();
        }
    }

    public Map<String, BaseRateDto> getCalculatedRates(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<String> keys = symbols.stream()
                    .map(this::buildCalculatedRateKey)
                    .collect(Collectors.toList());

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

            return result;

        } catch (Exception e) {
            log.error("Failed to get calculated rates in batch", e);
            return Collections.emptyMap();
        }
    }

    public BaseRateDto getLatestCalculatedRate(String symbol) {
        if (symbol == null) {
            return null;
        }

        try {
            String key = buildCalculatedRateKey(symbol);
            return calculatedRateRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error retrieving calculated rate: {}", symbol, e);
            return null;
        }
    }

    public BaseRateDto getCalculatedRate(String symbol) {
        return getLatestCalculatedRate(symbol);
    }

    private String buildRawRateKey(String symbol, String providerName) {
        return String.format("%s:raw:%s:%s", keyPrefix, symbol, providerName);
    }

    private String buildCalculatedRateKey(String symbol) {
        return String.format("%s:calc:%s", keyPrefix, symbol);
    }

    public Map<String, Object> getCacheStats() {
        return Map.of(
            "rawRateTtlSeconds", rawRateTtlSeconds,
            "calculatedRateTtlSeconds", calculatedRateTtlSeconds,
            "keyPrefix", keyPrefix
        );
    }

    

}