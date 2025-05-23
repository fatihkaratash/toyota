package com.toyota.consumer.util;

import com.toyota.consumer.model.RateEntity; // Updated import
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
     * 
     * @param message the message to parse
     * @return an Optional containing the parsed RateEntity, or an empty Optional if parsing failed
     */
    public Optional<RateEntity> parseToEntity(String message) {
        if (message == null || message.isBlank()) {
            log.warn("Cannot parse null or empty message");
            return Optional.empty();
        }
        
        try {
            String[] parts = message.split(DELIMITER);
            if (parts.length < 4) {
                log.error("Invalid message format, expected 4 parts but got {}: {}", parts.length, message);
                return Optional.empty();
            }
            
            // parts[0] is RateEntity.rateName
            String rateName = parts[0];
            
            // Parse bid and ask
            BigDecimal bid = parseDecimal(parts[1]);
            BigDecimal ask = parseDecimal(parts[2]);
            
            // Parse timestamp
            LocalDateTime rateUpdatetime = parseTimestamp(parts[3]);

            if (bid == null || ask == null || rateUpdatetime == null) {
                log.warn("Failed to parse one or more critical fields (bid, ask, timestamp) for message: {}", message);
                return Optional.empty();
            }
            
            RateEntity rateEntity = RateEntity.builder()
                    .rateName(rateName)
                    .bid(bid)
                    .ask(ask)
                    .rateUpdatetime(rateUpdatetime)
                    // dbUpdatetime will be set by @CreationTimestamp or PersistenceService
                    .build();
                    
            return Optional.of(rateEntity);
                    
        } catch (Exception e) {
            log.error("Error parsing message to RateEntity: {}", message, e);
            return Optional.empty();
        }
    }
    
    private BigDecimal parseDecimal(String value) {
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
    }
}
