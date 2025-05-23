package com.toyota.mainapp.calculator.engine;

import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;

import java.util.Map;
import java.util.Optional;

/**
 * Farklı hesaplama stratejileri için arayüz
 */
public interface CalculationStrategy {
    
    /**
     * Verilen kural ve giriş kurlarına göre hesaplama yap
     *
     * @param rule Hesaplama kuralı ve yapılandırması
     * @param inputRates Hesaplama için gereken giriş kurları
     * @return Hesaplanmış kur (başarısız olursa boş)
     */
    Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates);
    
    /**
     * Stratejinin benzersiz adını döndür
     *
     * @return Strateji adı
     */
    String getStrategyName();
}
