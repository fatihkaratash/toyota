package com.toyota.mainapp.cache;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateCacheService {

    private final RedisTemplate<String, BaseRateDto> redisTemplate;

    // ✅ TTL values updated for real-time pipeline
    private static final Duration RAW_RATE_TTL = Duration.ofSeconds(15); // 15s per refactor
    private static final Duration CALCULATED_RATE_TTL = Duration.ofSeconds(10); // 10s per refactor

    /**
     * Raw rate'i cache'e al - METHOD SIGNATURE GÜNCELLENDİ
     */
    public void cacheRawRate(String unusedParameter, BaseRateDto rate) {
        // İlk parametre backward compatibility için var ama kullanılmıyor
        cacheRawRate(rate);
    }

    /**
     * ✅ Raw rate'i cache'e al - ENHANCED METHOD SIGNATURE
     * Backward compatibility maintained
     */
    public void cacheRawRate(BaseRateDto rate) {
        if (rate == null || rate.getSymbol() == null) {
            log.warn("Null rate cache'lenemez");
            return;
        }

        // Key'i normalize et
        String normalizedSymbol = SymbolUtils.normalizeSymbol(rate.getSymbol());
        if (!SymbolUtils.isValidSymbol(normalizedSymbol)) {
            log.warn("Invalid symbol, cache'lenemez: '{}'", rate.getSymbol());
            return;
        }

        // TEK FORMAT KEY: symbol + provider
        String cacheKey = generateRawRateKey(normalizedSymbol, rate.getProviderName());

        try {
            redisTemplate.opsForValue().set(cacheKey, rate, RAW_RATE_TTL);
            log.debug("Raw rate cached: {}", cacheKey);
        } catch (Exception e) {
            log.error("Raw rate cache error: {} - {}", cacheKey, e.getMessage(), e);
        }
    }

    /**
     * Calculated rate'i cache'e al
     */
    public void cacheCalculatedRate(BaseRateDto rate) {
        if (rate == null || rate.getSymbol() == null) {
            log.warn("Null calculated rate cache'lenemez");
            return;
        }

        String normalizedSymbol = SymbolUtils.normalizeSymbol(rate.getSymbol());
        if (!SymbolUtils.isValidSymbol(normalizedSymbol)) {
            log.warn("Invalid calculated symbol, cache'lenemez: '{}'", rate.getSymbol());
            return;
        }

        String calculationType = determineCalculationType(rate);
        String cacheKey = generateCalculatedRateKey(normalizedSymbol, calculationType);

        try {
            redisTemplate.opsForValue().set(cacheKey, rate, CALCULATED_RATE_TTL);
            log.debug("Calculated rate cached: {}", cacheKey);
        } catch (Exception e) {
            log.error("Calculated rate cache error: {} - {}", cacheKey, e.getMessage(), e);
        }
    }

    /**
     * Symbol için raw rate'leri al
     */
    public Set<BaseRateDto> getRawRatesBySymbol(String symbol) {
        String normalizedSymbol = SymbolUtils.normalizeSymbol(symbol);
        if (!SymbolUtils.isValidSymbol(normalizedSymbol)) {
            log.warn("Invalid symbol for cache lookup: '{}'", symbol);
            return Set.of();
        }

        try {
            String pattern = "raw_rate:" + normalizedSymbol + ":*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys == null || keys.isEmpty()) {
                log.debug("No raw rates found for symbol: {}", normalizedSymbol);
                return Set.of();
            }

            List<BaseRateDto> rates = redisTemplate.opsForValue().multiGet(keys);
            return rates != null ? Set.copyOf(rates) : Set.of();

        } catch (Exception e) {
            log.error("Raw rates lookup error for symbol {}: {}", normalizedSymbol, e.getMessage(), e);
            return Set.of();
        }
    }

    /**
     * Calculated rate al - İKİ PARAMETRE
     */
    public BaseRateDto getCalculatedRate(String symbol, String calculationType) {
        String normalizedSymbol = SymbolUtils.normalizeSymbol(symbol);
        if (!SymbolUtils.isValidSymbol(normalizedSymbol)) {
            log.warn("Invalid symbol for calculated rate lookup: '{}'", symbol);
            return null;
        }

        String cacheKey = generateCalculatedRateKey(normalizedSymbol, calculationType);

        try {
            BaseRateDto rate = redisTemplate.opsForValue().get(cacheKey);
            if (rate != null) {
                log.debug("Calculated rate found: {}", cacheKey);
            } else {
                log.debug("Calculated rate not found: {}", cacheKey);
            }
            return rate;
        } catch (Exception e) {
            log.error("Calculated rate lookup error: {} - {}", cacheKey, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Calculated rate al - TEK PARAMETRE (CrossRateCollector için)
     * Default olarak AVG type'ı dener, bulamazsa diğer type'ları dener
     */
    public Optional<BaseRateDto> getCalculatedRate(String symbol) {
        String normalizedSymbol = SymbolUtils.normalizeSymbol(symbol);
        if (!SymbolUtils.isValidSymbol(normalizedSymbol)) {
            log.warn("Invalid symbol for calculated rate lookup: '{}'", symbol);
            return Optional.empty();
        }

        // 1. Önce AVG dene
        BaseRateDto rate = getCalculatedRate(normalizedSymbol, "AVG");
        if (rate != null) {
            return Optional.of(rate);
        }

        // 2. CROSS dene
        rate = getCalculatedRate(normalizedSymbol, "CROSS");
        if (rate != null) {
            return Optional.of(rate);
        }

        // 3. CALC dene
        rate = getCalculatedRate(normalizedSymbol, "CALC");
        if (rate != null) {
            return Optional.of(rate);
        }

        // 4. Pattern matching ile ara
        try {
            String pattern = "calc_rate:" + normalizedSymbol + ":*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                String firstKey = keys.iterator().next();
                rate = redisTemplate.opsForValue().get(firstKey);
                if (rate != null) {
                    log.debug("Found calculated rate with pattern match: {}", firstKey);
                    return Optional.of(rate);
                }
            }
        } catch (Exception e) {
            log.error("Pattern lookup error for symbol {}: {}", normalizedSymbol, e.getMessage(), e);
        }

        log.debug("No calculated rate found for symbol: {}", normalizedSymbol);
        return Optional.empty();
    }

    // HELPER METHODS

    private String determineCalculationType(BaseRateDto rate) {
        // Symbol'den calculation type'ı çıkar
        String symbol = rate.getSymbol();
        if (symbol.contains("CROSS")) {
            return "CROSS";
        } else if (symbol.contains("AVG")) {
            return "AVG";
        } else if (symbol.contains("CALC")) {
            return "CALC";
        }

        // Default AVG
        return "AVG";
    }

    private String generateRawRateKey(String normalizedSymbol, String providerName) {
        // TEK FORMAT: raw_rate:SYMBOL:PROVIDER
        return String.format("raw_rate:%s:%s",
                SymbolUtils.normalizeSymbol(normalizedSymbol), providerName);
    }

    private String generateCalculatedRateKey(String normalizedSymbol, String calculationType) {
        // TEK FORMAT: calc_rate:SYMBOL:TYPE
        String normalized = SymbolUtils.normalizeSymbol(normalizedSymbol);
        return String.format("calc_rate:%s:%s",
                normalized, calculationType != null ? calculationType : "AVG");
    }

    /**
     * Cache temizleme - memory leak önlemi
     */
    public void cleanupExpiredRates() {
        try {
            // Redis TTL otomatik temizlik yapar, ama manual cleanup de eklenebilir
            log.debug("Cache cleanup completed");
        } catch (Exception e) {
            log.error("Cache cleanup error: {}", e.getMessage(), e);
        }
    }

    // ✅ ENHANCED: Batch get method for pipeline optimization  
    public Map<String, BaseRateDto> getRequiredRawRatesBatch(List<String> symbols, String providerName) {
        Map<String, BaseRateDto> result = new HashMap<>();
        
        List<String> keys = symbols.stream()
                .map(symbol -> generateRawRateKey(SymbolUtils.normalizeSymbol(symbol), providerName))
                .collect(java.util.stream.Collectors.toList());
        
        try {
            List<BaseRateDto> rates = redisTemplate.opsForValue().multiGet(keys);
            for (int i = 0; i < keys.size() && rates != null && i < rates.size(); i++) {
                if (rates.get(i) != null) {
                    result.put(symbols.get(i), rates.get(i));
                }
            }
            log.debug("Batch raw rates retrieved: {}/{}", result.size(), symbols.size());
        } catch (Exception e) {
            log.error("Batch raw rates lookup error: {}", e.getMessage(), e);
        }
        
        return result;
    }

    /**
     * ✅ ENHANCED: Batch get method for calculated rates
     */  
    public Map<String, BaseRateDto> getRequiredCalculatedRatesBatch(List<String> symbols) {
        Map<String, BaseRateDto> result = new HashMap<>();
        
        for (String symbol : symbols) {
            Optional<BaseRateDto> rate = getCalculatedRate(symbol);
            rate.ifPresent(r -> result.put(symbol, r));
        }
        
        log.debug("Batch calculated rates retrieved: {}/{}", result.size(), symbols.size());
        return result;
    }
}