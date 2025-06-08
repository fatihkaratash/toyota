package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputRateInfo {

    private String symbol;
    private String rateType;
    private String providerName;
    private BigDecimal bid;
    private BigDecimal ask;
    private Long timestamp;
    
    /**
      FACTORY: Create from BaseRateDto with validation
     */
    public static InputRateInfo fromBaseRateDto(BaseRateDto rate) {
        if (rate == null) {
            return null;
        }

        if (rate.getBid() == null || rate.getAsk() == null) {
            return null;
        }
        
        return InputRateInfo.builder()
                .symbol(rate.getSymbol())
                .rateType(rate.getRateType() != null ? rate.getRateType().toString() : "UNKNOWN")
                .providerName(rate.getProviderName())
                .bid(rate.getBid())
                .ask(rate.getAsk())
                .timestamp(rate.getTimestamp())
                .build();
    }
}
