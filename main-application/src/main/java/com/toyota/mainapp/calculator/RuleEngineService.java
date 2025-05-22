package com.toyota.mainapp.calculator;

import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import com.toyota.mainapp.dto.RawRateDto;

import java.util.List;
import java.util.Map;

/**
 * Hesaplama kurallarını yöneten ve hesaplamaları yapan servis
 */
public interface RuleEngineService {
    
    /**
     * Belirtilen giriş sembolünü kullanan tüm hesaplama kurallarını bul
     * 
     * @param symbol Aranacak giriş sembolü
     * @return Bu sembolü kullanan hesaplama kurallarının listesi
     */
    List<CalculationRuleDto> getRulesByInputSymbol(String symbol);
    
    /**
     * Bir hesaplama kuralını verilen giriş kurları ile çalıştır
     * 
     * @param rule Çalıştırılacak hesaplama kuralı
     * @param inputRates Hesaplama için gereken giriş kurları
     * @return Hesaplanmış kur (başarısız olursa null)
     */
    CalculatedRateDto executeRule(CalculationRuleDto rule, Map<String, RawRateDto> inputRates);
    
    /**
     * Tüm hesaplama kurallarını yükle
     */
    void loadRules();
    
    /**
     * Tüm kayıtlı hesaplama kurallarını al
     * 
     * @return Tüm hesaplama kurallarının listesi
     */
    List<CalculationRuleDto> getAllRules();
    
    /**
     * Bir hesaplama kuralını ekle veya güncelle
     *
     * @param rule Eklemek veya güncellemek için kural
     */
    void addRule(CalculationRuleDto rule);
}
