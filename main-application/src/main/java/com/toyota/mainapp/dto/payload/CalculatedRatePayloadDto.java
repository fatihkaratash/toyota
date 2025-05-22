package com.toyota.mainapp.dto.payload;

import com.toyota.mainapp.dto.common.InputRateInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Hesaplanmış kur verisinin Kafka yük formatı
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatedRatePayloadDto {
    
    /**
     * Hesaplanmış kurun sembolü
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
     * Hesaplama zamanı
     */
    private Long rateTimestamp;
    
    /**
     * Hesaplamada kullanılan giriş kurları
     */
    @Builder.Default
    private List<InputRateInfo> calculationInputs = new ArrayList<>();
    
    /**
     * Hesaplama için kullanılan strateji
     */
    private String calculatedByStrategy;
    
    /**
     * Olay tipi - sürekli "CALCULATED_RATE"
     */
    private String eventType;
    
    /**
     * Olayın Kafka'ya gönderilme zamanı
     */
    private Long eventTime;
}
