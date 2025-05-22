package com.toyota.mainapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rate {
    private String symbol;          // e.g., USDTRY, EURUSD
    private BigDecimal bid;         // Bid price
    private BigDecimal ask;         // Ask price
    private Instant timestamp;      // Timestamp of the rate
    private String providerName;    // Name of the platform providing this rate
    private RateStatus status;      // Status of the rate (e.g., VALID, STALE)
}
