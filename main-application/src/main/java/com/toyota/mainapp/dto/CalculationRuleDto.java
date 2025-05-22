package com.toyota.mainapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Hesaplama kuralı yapılandırma DTO'su
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationRuleDto {
    
    /**
     * Hesaplanmış kurun sembol adı (örn: "EUR/TRY")
     */
    private String outputSymbol;
    
    /**
     * Hesaplama kuralının açıklaması
     */
    private String description;
    
    /**
     * Hesaplama stratejisinin tipi ("JAVA_CLASS" veya "GROOVY_SCRIPT")
     */
    private String strategyType;
    
    /**
     * Uygulama sınıf adı veya script yolu
     */
    private String implementation;
    
    /**
     * Hesaplama için kullanılacak ham kurların sembolleri
     */
    private String[] dependsOnRaw;
    
    /**
     * Hesaplama için kullanılacak hesaplanmış kurların sembolleri
     */
    private String[] dependsOnCalculated;
    
    /**
     * Kural önceliği (düşük sayı = yüksek öncelik)
     */
    private int priority;
    
    /**
     * Hesaplama için ek parametreler
     */
    private Map<String, Object> inputParameters;
}
