package com.toyota.util;

import com.toyota.model.RateEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Utility class to parse rate messages from Kafka
 * Format: SYMBOL|BID|ASK|TIMESTAMP
 * Example: PF1_USDTRY|33.60|35.90|2024-12-16T16:07:15.504
 */
@Component
@Slf4j
public class RateParser {

    /**
     * Parses a pipe-delimited message to RateEntity
     * Format: SYMBOL|BID|ASK|TIMESTAMP
     * 
     * @param message the message to parse
     * @return an Optional containing the parsed RateEntity, or empty if parsing failed
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
            BigDecimal bid = parseDecimal(parts[1]);
            BigDecimal ask = parseDecimal(parts[2]);
            LocalDateTime timestamp = parseTimestamp(parts[3]);

            if (bid == null || ask == null || timestamp == null) {
                log.warn("Failed to parse required fields from message: {}", message);
                return Optional.empty();
            }

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
            // Try with milliseconds first
            if (value.contains(".")) {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
            } else {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse timestamp value: {}", value);
            return null;
        }
    }
}