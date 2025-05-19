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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set; // Added import for Set
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Override
    public void cacheRawRate(Rate rate) {
        try {
            String key = generateRawRateKey(rate.getSymbol(), rate.getPlatformName());
            redisTemplate.opsForValue().set(key, rate, rawRatesTtlSeconds, TimeUnit.SECONDS);
            logger.debug("Ham kur önbelleğe alındı: {} platform: {}", rate.getSymbol(), rate.getPlatformName());
        } catch (RedisConnectionFailureException e) {
            logger.error("Ham kur önbelleğe alınırken Redis bağlantı hatası: {}", e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle ham kur önbelleğe alınamadı", e);
        } catch (Exception e) {
            logger.error("Ham kur önbelleğe alınırken hata: {}", e.getMessage(), e);
            throw new CacheException("Ham kur önbelleğe alınamadı", e);
        }
    }

    @Override
    public void cacheCalculatedRate(CalculatedRate rate) {
        try {
            String key = generateCalculatedRateKey(rate.getSymbol());
            redisTemplate.opsForValue().set(key, rate, calculatedRatesTtlSeconds, TimeUnit.SECONDS);
            logger.debug("Hesaplanmış kur önbelleğe alındı: {}", rate.getSymbol());
        } catch (RedisConnectionFailureException e) {
            logger.error("Hesaplanmış kur önbelleğe alınırken Redis bağlantı hatası: {}", e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle hesaplanmış kur önbelleğe alınamadı", e);
        } catch (Exception e) {
            logger.error("Hesaplanmış kur önbelleğe alınırken hata: {}", e.getMessage(), e);
            throw new CacheException("Hesaplanmış kur önbelleğe alınamadı", e);
        }
    }

    @Override
    public Optional<Rate> getRawRate(String symbol, String platformName) {
        try {
            String key = generateRawRateKey(symbol, platformName);
            Object result = redisTemplate.opsForValue().get(key);
            if (result instanceof Rate) {
                logger.debug("Ham kur getirildi: {} platform: {}", symbol, platformName);
                return Optional.of((Rate) result);
            }
            logger.debug("Ham kur önbellekte bulunamadı: {} platform: {}", symbol, platformName);
            return Optional.empty();
        } catch (RedisConnectionFailureException e) {
            logger.error("Ham kur getirilirken Redis bağlantı hatası: {}", e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle ham kur getirilemedi", e);
        } catch (Exception e) {
            logger.error("Ham kur getirilirken hata: {}", e.getMessage(), e);
            throw new CacheException("Ham kur getirilemedi", e);
        }
    }

    @Override
    public Optional<CalculatedRate> getCalculatedRate(String symbol) {
        try {
            String key = generateCalculatedRateKey(symbol);
            Object result = redisTemplate.opsForValue().get(key);
            if (result instanceof CalculatedRate) {
                logger.debug("Hesaplanmış kur getirildi: {}", symbol);
                return Optional.of((CalculatedRate) result);
            }
            logger.debug("Hesaplanmış kur önbellekte bulunamadı: {}", symbol);
            return Optional.empty();
        } catch (RedisConnectionFailureException e) {
            logger.error("Hesaplanmış kur getirilirken Redis bağlantı hatası: {}", e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle hesaplanmış kur getirilemedi", e);
        } catch (Exception e) {
            logger.error("Hesaplanmış kur getirilirken hata: {}", e.getMessage(), e);
            throw new CacheException("Hesaplanmış kur getirilemedi", e);
        }
    }

    @Override
    public Map<String, Rate> getAllRawRates(String symbolPrefix) {
        try {
            Map<String, Rate> result = new HashMap<>();
            String keyPattern = RAW_RATE_KEY_PREFIX + "*" + symbolPrefix + "*";
            
            // Note: Using Redis KEYS command here - should be replaced with SCAN for production at scale
            // as KEYS can block the Redis server if there are many keys
            Set<String> keys = redisTemplate.keys(keyPattern);
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value instanceof Rate) {
                        Rate rate = (Rate) value;
                        // Use symbol from rate object as key if platform name is not unique enough or not desired
                        // For now, assuming platformName is the intended key from the original logic
                        result.put(rate.getPlatformName(), rate);
                    }
                }
                logger.debug("Sembol öneki için {} adet ham kur getirildi: {}", result.size(), symbolPrefix);
            }
            return result;
        } catch (RedisConnectionFailureException e) {
            logger.error("Tüm ham kurlar getirilirken Redis bağlantı hatası: {}", e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle tüm ham kurlar getirilemedi", e);
        } catch (Exception e) {
            logger.error("Tüm ham kurlar getirilirken hata: {}", e.getMessage(), e);
            throw new CacheException("Tüm ham kurlar getirilemedi", e);
        }
    }

    @Override
    public Map<String, CalculatedRate> getAllCalculatedRates() {
        try {
            Map<String, CalculatedRate> result = new HashMap<>();
            String keyPattern = CALCULATED_RATE_KEY_PREFIX + "*";
            
            // Note: Using Redis KEYS command here - should be replaced with SCAN for production at scale
            Set<String> keys = redisTemplate.keys(keyPattern);
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value instanceof CalculatedRate) {
                        CalculatedRate rate = (CalculatedRate) value;
                        result.put(rate.getSymbol(), rate);
                    }
                }
                logger.debug("{} adet hesaplanmış kur getirildi", result.size());
            }
            return result;
        } catch (RedisConnectionFailureException e) {
            logger.error("Tüm hesaplanmış kurlar getirilirken Redis bağlantı hatası: {}", e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle tüm hesaplanmış kurlar getirilemedi", e);
        } catch (Exception e) {
            logger.error("Tüm hesaplanmış kurlar getirilirken hata: {}", e.getMessage(), e);
            throw new CacheException("Tüm hesaplanmış kurlar getirilemedi", e);
        }
    }

    @Override
    public void clearAll() {
        try {
            // Clear raw rates
            String rawRatePattern = RAW_RATE_KEY_PREFIX + "*";
            Set<String> rawRateKeys = redisTemplate.keys(rawRatePattern);
            if (rawRateKeys != null && !rawRateKeys.isEmpty()) {
                redisTemplate.delete(rawRateKeys);
            }
            
            // Clear calculated rates
            String calculatedRatePattern = CALCULATED_RATE_KEY_PREFIX + "*";
            Set<String> calculatedRateKeys = redisTemplate.keys(calculatedRatePattern);
            if (calculatedRateKeys != null && !calculatedRateKeys.isEmpty()) {
                redisTemplate.delete(calculatedRateKeys);
            }
            
            logger.info("Tüm önbelleklenmiş kurlar temizlendi");
        } catch (RedisConnectionFailureException e) {
            logger.error("Önbellek temizlenirken Redis bağlantı hatası: {}", e.getMessage());
            throw new CacheException("Redis bağlantı hatası nedeniyle önbellek temizlenemedi", e);
        } catch (Exception e) {
            logger.error("Önbellek temizlenirken hata: {}", e.getMessage(), e);
            throw new CacheException("Önbellek temizlenemedi", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // A more robust check might involve a PING command or setting/getting a test key.
            // For now, checking if a known key pattern exists or if connection is active.
            // LettuceConnectionFactory connectionFactory = (LettuceConnectionFactory) redisTemplate.getConnectionFactory();
            // return connectionFactory != null && connectionFactory.getConnection().ping() != null;
            // Simplified check:
            redisTemplate.hasKey("health:check:dummy"); // This attempts an operation
            return true;
        } catch (Exception e) {
            logger.error("Redis önbelleği mevcut değil: {}", e.getMessage());
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
