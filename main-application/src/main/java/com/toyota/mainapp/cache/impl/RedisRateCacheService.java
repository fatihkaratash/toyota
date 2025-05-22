package com.toyota.mainapp.cache.impl;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.RawRateDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RedisRateCacheService implements RateCacheService {

    private static final String RAW_RATE_PREFIX = "raw_rate:";
    private static final String CALCULATED_RATE_PREFIX = "calc_rate:";

    private final RedisTemplate<String, RawRateDto> rawRateRedisTemplate;
    private final RedisTemplate<String, CalculatedRateDto> calculatedRateRedisTemplate;
    private final long rawRateTtlSeconds;
    private final long calculatedRateTtlSeconds;

    public RedisRateCacheService(
            @Qualifier("rawRateRedisTemplate") RedisTemplate<String, RawRateDto> rawRateRedisTemplate,
            @Qualifier("calculatedRateRedisTemplate") RedisTemplate<String, CalculatedRateDto> calculatedRateRedisTemplate,
            @Value("${app.cache.raw-rate.ttl-seconds}") long rawRateTtlSeconds,
            @Value("${app.cache.calculated-rate.ttl-seconds}") long calculatedRateTtlSeconds) {
        this.rawRateRedisTemplate = rawRateRedisTemplate;
        this.calculatedRateRedisTemplate = calculatedRateRedisTemplate;
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
    public void cacheRawRate(String key, RawRateDto rawRateDto) {
        String cacheKey = buildRawRateCacheKey(key);
        try {
            rawRateRedisTemplate.opsForValue().set(cacheKey, rawRateDto, rawRateTtlSeconds, TimeUnit.SECONDS);
            log.debug("Ham kur {}: {} anahtariyla onbellege alindi.", cacheKey, rawRateDto);
        } catch (Exception e) {
            log.error("{} anahtarli ham kur onbellege alinirken hata: {}", cacheKey, e.getMessage(), e);
        }
    }

    @Override
    public Optional<RawRateDto> getRawRate(String key) {
        String cacheKey = buildRawRateCacheKey(key);
        try {
            RawRateDto rawRate = rawRateRedisTemplate.opsForValue().get(cacheKey);
            if (rawRate != null) {
                log.debug("{} anahtarli ham kur onbellekten alindi: {}", cacheKey, rawRate);
                return Optional.of(rawRate);
            } else {
                log.debug("{} anahtarli ham kur onbellekte bulunamadi.", cacheKey);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("{} anahtarli ham kur onbellekten alinirken hata: {}", cacheKey, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheCalculatedRate(CalculatedRateDto calculatedRateDto) {
        if (calculatedRateDto == null || calculatedRateDto.getSymbol() == null) {
            log.warn("Null CalculatedRateDto veya null sembollu DTO onbellege alinamiyor.");
            return;
        }
        String cacheKey = buildCalculatedRateCacheKey(calculatedRateDto.getSymbol());
        try {
            calculatedRateRedisTemplate.opsForValue().set(cacheKey, calculatedRateDto, calculatedRateTtlSeconds, TimeUnit.SECONDS);
            log.debug("Hesaplanmis kur {}: {} anahtariyla onbellege alindi.", cacheKey, calculatedRateDto);
        } catch (Exception e) {
            log.error("{} anahtarli hesaplanmis kur onbellege alinirken hata: {}", cacheKey, e.getMessage(), e);
        }
    }

    @Override
    public Optional<CalculatedRateDto> getCalculatedRate(String symbol) {
        if (symbol == null) {
            log.warn("Null sembol icin hesaplanmis kur alinamiyor.");
            return Optional.empty();
        }
        String cacheKey = buildCalculatedRateCacheKey(symbol);
        try {
            CalculatedRateDto calculatedRate = calculatedRateRedisTemplate.opsForValue().get(cacheKey);
            if (calculatedRate != null) {
                log.debug("{} anahtarli hesaplanmis kur onbellekten alindi: {}", cacheKey, calculatedRate);
                return Optional.of(calculatedRate);
            } else {
                log.debug("{} anahtarli hesaplanmis kur onbellekte bulunamadi.", cacheKey);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("{} anahtarli hesaplanmis kur onbellekten alinirken hata: {}", cacheKey, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
        public java.util.List<RawRateDto> getAllRawRatesForSymbol(String symbol) {
            // TODO: Implement logic to fetch all raw rates for the given symbol from Redis
            log.warn("getAllRawRatesForSymbol is not yet implemented.");
            return java.util.Collections.emptyList();
        }
}
