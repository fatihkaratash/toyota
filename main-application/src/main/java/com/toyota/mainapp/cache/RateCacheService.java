package com.toyota.mainapp.cache;

import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.RateType;

import java.util.List;
import java.util.Optional;

/**
 * Kur verilerini önbellekleme servisi
 */
public interface RateCacheService {
    
    /**
     * Herhangi bir kur verisini önbelleğe al
     * @param key Önbellek anahtarı
     * @param rateDto Kur verisi
     */
    void cacheRate(String key, BaseRateDto rateDto);
    
    /**
     * Kurları önbellekten al
     * @param key Önbellek anahtarı
     * @return Kur verisi
     */
    Optional<BaseRateDto> getRate(String key);
    
    /**
     * Ham kuru önbelleğe al
     * @param key Önbellek anahtarı
     * @param rawRate Ham kur verisi
     */
    default void cacheRawRate(String key, BaseRateDto rawRate) {
        if (rawRate != null) {
            rawRate.setRateType(RateType.RAW);
            cacheRate(key, rawRate);
        }
    }
    
    /**
     * Hesaplanmış kuru önbelleğe al
     * @param calculatedRate Hesaplanmış kur
     */
    void cacheCalculatedRate(BaseRateDto calculatedRate);
    
    /**
     * Hesaplanmış kuru önbellekten al
     * @param symbol Kur sembolü
     * @return Hesaplanmış kur
     */
    Optional<BaseRateDto> getCalculatedRate(String symbol);
    
    /**
     * Belirli bir sembole ait tüm ham kurları al
     * @param symbol Sembol
     * @return Ham kurlar listesi
     */
    List<BaseRateDto> getAllRawRatesForSymbol(String symbol);
}
