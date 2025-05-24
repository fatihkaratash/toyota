package com.toyota.mainapp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputRateInfo {
    private String symbol;
    private String rateType;
    private String providerName;
    private BigDecimal bid;
    private BigDecimal ask;
    private Long timestamp;
}
