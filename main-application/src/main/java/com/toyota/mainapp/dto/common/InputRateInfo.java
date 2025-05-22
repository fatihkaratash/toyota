package com.toyota.mainapp.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Hesaplamada kullanılan giriş kurlarını temsil eden DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputRateInfo {
    
    /**
     * Kur sembol adı
     */
    private String symbol;
    
    /**
     * Kur tipi (HAM veya HESAPLANMIŞ)
     */
    private String rateType;
    
    /**
     * Veri sağlayıcı adı
     */
    private String providerName;
    
    /**
     * Kullanılan alış fiyatı
     */
    private BigDecimal bid;
    
    /**
     * Kullanılan satış fiyatı
     */
    private BigDecimal ask;
    
    /**
     * Kullanılan verinin zaman damgası
     */
    private Long timestamp;
}
