package com.toyota.mainapp.kafka.message;

import com.toyota.mainapp.model.RateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Message sent to Kafka containing rate information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateMessage {
    private String messageId;       // Unique ID for this message
    private String symbol;
    private BigDecimal bid;
    private BigDecimal ask;
    private Instant timestamp;
    private String providerName;    // "RAW_PROVIDER_NAME" or "CALCULATED"
    private RateStatus status;
    private boolean isCalculated;
    private List<String> baseRateSymbols; // Populated if isCalculated is true
    private String calculationFormulaId;  // Populated if isCalculated is true
    private Instant processedTimestamp; // Timestamp when this message was created/processed by coordinator
}
