package com.toyota.mainapp.cache.impl;

import com.toyota.mainapp.cache.RateCache;
import com.toyota.mainapp.cache.exception.CacheException;
import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis implementation of the RateCache interface.
 * Uses Redis as a backing store for both raw and calculated rates.
 */
@Service
public class RedisRateCache implements RateCache {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateCache.class);

    private static final String RAW_RATE_KEY_PREFIX = "rate:raw:";
    private static final String CALCULATED_RATE_KEY_PREFIX = "rate:calculated:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final long rawRatesTtlSeconds;
    private final long calculatedRatesTtlSeconds;

    @Autowired
    public RedisRateCache(RedisTemplate<String, Object> redisTemplate,
                         long rawRatesTtlSeconds,
                         long calculatedRatesTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.rawRatesTtlSeconds = rawRatesTtlSeconds;
        this.calculatedRatesTtlSeconds = calculatedRatesTtlSeconds;
    }

    private <T> T executeGetter(String operationDescription, Supplier<T> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (RedisConnectionFailureException e) {
            logger.error("{} sırasında Redis bağlantı hatası: {}", operationDescription, e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle " + operationDescription + " başarısız oldu", e);
        } catch (Exception e) {
            logger.error("{} sırasında hata: {}", operationDescription, e.getMessage(), e);
            // For getters, we might not always want to throw.
            // Depending on strictness, could return defaultValue or throw CacheException.
            // For now, let's be strict and throw. If lenient needed, return defaultValue.
            throw new CacheException(operationDescription + " başarısız oldu", e);
        }
    }
    
    private void executeSetter(String operationDescription, Runnable runnable) {
        try {
            runnable.run();
        } catch (RedisConnectionFailureException e) {
            logger.error("{} sırasında Redis bağlantı hatası: {}", operationDescription, e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle " + operationDescription + " başarısız oldu", e);
        } catch (Exception e) {
            logger.error("{} sırasında hata: {}", operationDescription, e.getMessage(), e);
            throw new CacheException(operationDescription + " başarısız oldu", e);
        }
    }


    @Override
    public void cacheRawRate(Rate rate) {
        executeSetter("Ham kur önbelleğe alma", () -> {
            String key = generateRawRateKey(rate.getSymbol(), rate.getPlatformName());
            redisTemplate.opsForValue().set(key, rate, rawRatesTtlSeconds, TimeUnit.SECONDS);
            logger.debug("Ham kur önbelleğe alındı: {} platform: {}", rate.getSymbol(), rate.getPlatformName());
        });
    }

    @Override
    public void cacheCalculatedRate(CalculatedRate rate) {
        executeSetter("Hesaplanmış kur önbelleğe alma", () -> {
            String key = generateCalculatedRateKey(rate.getSymbol());
            redisTemplate.opsForValue().set(key, rate, calculatedRatesTtlSeconds, TimeUnit.SECONDS);
            logger.debug("Hesaplanmış kur önbelleğe alındı: {}", rate.getSymbol());
        });
    }

    @Override
    public Optional<Rate> getRawRate(String symbol, String platformName) {
        return executeGetter("Ham kur getirme", () -> {
            String key = generateRawRateKey(symbol, platformName);
            Object result = redisTemplate.opsForValue().get(key);
            if (result instanceof Rate) {
                logger.debug("Ham kur getirildi: {} platform: {}", symbol, platformName);
                return Optional.of((Rate) result);
            }
            logger.debug("Ham kur önbellekte bulunamadı: {} platform: {}", symbol, platformName);
            return Optional.empty();
        }, Optional.empty());
    }

    @Override
    public Optional<CalculatedRate> getCalculatedRate(String symbol) {
        return executeGetter("Hesaplanmış kur getirme", () -> {
            String key = generateCalculatedRateKey(symbol);
            Object result = redisTemplate.opsForValue().get(key);
            if (result instanceof CalculatedRate) {
                logger.debug("Hesaplanmış kur getirildi: {}", symbol);
                return Optional.of((CalculatedRate) result);
            }
            logger.debug("Hesaplanmış kur önbellekte bulunamadı: {}", symbol);
            return Optional.empty();
        }, Optional.empty());
    }

    @Override
    public Map<String, Rate> getAllRawRates(String symbolPrefix) {
        return executeGetter("Tüm ham kurları getirme", () -> {
            Map<String, Rate> result = new HashMap<>();
            // IMPORTANT: Using KEYS is not recommended for production. Use SCAN instead.
            Set<String> keys = redisTemplate.keys(RAW_RATE_KEY_PREFIX + "*" + symbolPrefix + "*");
            if (keys != null) {
                for (String key : keys) {
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value instanceof Rate) {
                        Rate rate = (Rate) value;
                        result.put(rate.getSymbol(), rate); // Assuming rate.getSymbol() is unique enough for the map key
                    }
                }
            }
            logger.debug("Sembol öneki için {} adet ham kur getirildi: {}", result.size(), symbolPrefix);
            return result;
        }, Collections.emptyMap());
    }

    @Override
    public Map<String, CalculatedRate> getAllCalculatedRates() {
        return executeGetter("Tüm hesaplanmış kurları getirme", () -> {
            Map<String, CalculatedRate> result = new HashMap<>();
            // IMPORTANT: Using KEYS is not recommended for production. Use SCAN instead.
            Set<String> keys = redisTemplate.keys(CALCULATED_RATE_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value instanceof CalculatedRate) {
                        CalculatedRate rate = (CalculatedRate) value;
                        result.put(rate.getSymbol(), rate);
                    }
                }
            }
            logger.debug("{} adet hesaplanmış kur getirildi", result.size());
            return result;
        }, Collections.emptyMap());
    }

    @Override
    public void clearAll() {
        executeSetter("Tüm önbelleği temizleme", () -> {
            // IMPORTANT: Using KEYS is not recommended for production. Use SCAN instead.
            Set<String> rawRateKeys = redisTemplate.keys(RAW_RATE_KEY_PREFIX + "*");
            if (rawRateKeys != null && !rawRateKeys.isEmpty()) {
                redisTemplate.delete(rawRateKeys);
            }
            
            Set<String> calculatedRateKeys = redisTemplate.keys(CALCULATED_RATE_KEY_PREFIX + "*");
            if (calculatedRateKeys != null && !calculatedRateKeys.isEmpty()) {
                redisTemplate.delete(calculatedRateKeys);
            }
            logger.info("Tüm önbelleklenmiş kurlar temizlendi");
        });
    }

    @Override
    public boolean isAvailable() {
        try {
            // Lettuce PING is a lightweight way to check connection.
            // Connection is usually pooled, so this checks if a connection can be obtained and pinged.
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            logger.warn("Redis önbelleği mevcut değil veya bağlantı sorunu: {}", e.getMessage());
            return false;
        }
    }

    private String generateRawRateKey(String symbol, String platformName) {
        return RAW_RATE_KEY_PREFIX + platformName + ":" + symbol;
    }

    private String generateCalculatedRateKey(String symbol) {
        return CALCULATED_RATE_KEY_PREFIX + symbol;
    }
}
