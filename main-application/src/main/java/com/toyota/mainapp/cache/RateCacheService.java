package com.toyota.mainapp.cache;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public interface RateCacheService {

    void cacheRate(String key, BaseRateDto rateDto);

    Optional<BaseRateDto> getRate(String key);

    void cacheRawRate(String key, BaseRateDto rawRate);

    void cacheCalculatedRate(BaseRateDto calculatedRate);

    Optional<BaseRateDto> getCalculatedRate(String symbol);

    List<BaseRateDto> getAllRawRatesForSymbol(String symbol);

    Map<String, BaseRateDto> getAllCalculatedRates();

    BaseRateDto getCachedRate(String key);
}
