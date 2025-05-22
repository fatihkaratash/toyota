package com.toyota.mainapp.cache;

import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.RawRateDto;

import java.util.List;
import java.util.Optional;

/**
 * Kur verilerini önbellekleme servisi
 */
public interface RateCacheService {
    
    /**
     * Ham kuru önbelleğe al
     * @param key Önbellek anahtarı
     * @param rawRate Ham kur verisi
     */
    void cacheRawRate(String key, RawRateDto rawRate);
    
    /**
     * Ham kuru önbellekten al
     * @param key Önbellek anahtarı
     * @return Ham kur verisi
     */
    Optional<RawRateDto> getRawRate(String key);
    
    /**
     * Hesaplanmış kuru önbelleğe al
     * @param calculatedRate Hesaplanmış kur
     */
    void cacheCalculatedRate(CalculatedRateDto calculatedRate);
    
    /**
     * Hesaplanmış kuru önbellekten al
     * @param symbol Kur sembolü
     * @return Hesaplanmış kur
     */
    Optional<CalculatedRateDto> getCalculatedRate(String symbol);
    
    /**
     * Belirli bir sembole ait tüm ham kurları al
     * @param symbol Sembol
     * @return Ham kurlar listesi
     */
    List<RawRateDto> getAllRawRatesForSymbol(String symbol);
}
