package com.toyota.mainapp.dto;

import com.toyota.mainapp.dto.common.InputRateInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Hesaplanmış türev kur verilerini temsil eden DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatedRateDto {
    
    /**
     * Kur sembol adı (örn: "EUR/TRY")
     */
    private String symbol;
    
    /**
     * Alış fiyatı
     */
    private BigDecimal bid;
    
    /**
     * Satış fiyatı
     */
    private BigDecimal ask;
    
    /**
     * Hesaplama zamanı (milisaniye cinsinden epoch)
     */
    private Long timestamp;
    
    /**
     * Hesaplamada kullanılan giriş kurları
     */
    @Builder.Default
    private List<InputRateInfo> calculationInputs = new ArrayList<>();
    
    /**
     * Hesaplama için kullanılan strateji
     */
    private String calculatedByStrategy;
}
