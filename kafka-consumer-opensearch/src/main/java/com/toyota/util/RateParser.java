package main.java.com.toyota.util;

import com.toyota.consumer.model.RateEntity; // Updated import
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional; // Added import

/**
 * Utility class to parse rate messages from Kafka
 * Format: {PROVIDER}_{SYMBOL}|{BID}|{ASK}|{TIMESTAMP}
 * Example: TCPProvider2_USDTRY|38.61|38.79|2023-09-15T14:32:45.123
 */
@Component
@Slf4j
public class RateParser {

    private static final String DELIMITER = "\\|";
    // PROVIDER_SYMBOL_DELIMITER is no longer needed for the new parsing logic directly to rateName
    
    /**
     * Parses a string message into a RateEntity object
     *  * Parse pipe-delimited message to RateEntity
     * Format: SYMBOL|BID|ASK|TIMESTAMP
     * Example: PF1_USDTRY|33.60|35.90|2024-12-16T16:07:15.504
     * 
     * @param message the message to parse
     * @return an Optional containing the parsed RateEntity, or an empty Optional if parsing failed
     */
    public Optional<RateEntity> parseToEntity(String message) {
        if (message == null || message.trim().isEmpty()) {
            log.warn("Empty message received");
            return Optional.empty();
        }

        try {
            String[] parts = message.split("\\|");
            if (parts.length < 4) {
                log.warn("Invalid message format: {}", message);
                return Optional.empty();
            }

            String rateName = parts[0];
            BigDecimal bid = new BigDecimal(parts[1]);
            BigDecimal ask = new BigDecimal(parts[2]);
            LocalDateTime timestamp = LocalDateTime.parse(
                parts[3].substring(0, parts[3].length() - 4), // Remove milliseconds
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );

            return Optional.of(RateEntity.builder()
                .rateName(rateName)
                .bid(bid)
                .ask(ask)
                .rateUpdatetime(timestamp)
                .dbUpdatetime(LocalDateTime.now())
                .build());
        } catch (Exception e) {
            log.error("Failed to parse message: {}", message, e);
            return Optional.empty();
        }
    }
    
   /* private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse decimal value: {}", value);
            return null;
        }
    }
    
    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse timestamp value: {}", value);
            return null;
        }
    }*/
}
