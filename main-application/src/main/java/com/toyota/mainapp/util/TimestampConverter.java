package com.toyota.mainapp.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for converting timestamps between different formats
 */
public class TimestampConverter {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    /**
     * Convert a timestamp string to epoch milliseconds
     * 
     * Handles:
     * - Epoch milliseconds as string ("1624282380000")
     * - Epoch seconds as string ("1624282380")
     * - ISO 8601 format ("2021-06-21T12:33:00Z")
     * - ISO 8601 with millis ("2021-06-21T12:33:00.123Z")
     * 
     * @param timestampString Timestamp in string format
     * @return epoch milliseconds (UTC)
     * @throws IllegalArgumentException if the format cannot be recognized
     */
    public static long toEpochMillis(String timestampString) {
        if (timestampString == null || timestampString.isEmpty()) {
            throw new IllegalArgumentException("Timestamp string cannot be null or empty");
        }
        
        // First try parsing as a number (epoch seconds or millis)
        try {
            // If it's purely numeric, assume it's epoch format
            long timestamp = Long.parseLong(timestampString);
            // If it's seconds (10 digits or less), convert to millis
            if (timestampString.length() <= 10) {
                return timestamp * 1000;
            }
            return timestamp; // Already in millis
        } catch (NumberFormatException e) {
            // Not a number, try ISO format
        }
        
        // Try ISO format
        try {
            Instant instant = Instant.from(ISO_FORMATTER.parse(timestampString));
            return instant.toEpochMilli();
        } catch (DateTimeParseException e1) {
            // Try with a more lenient parser that can handle different ISO variants
            try {
                LocalDateTime dateTime = LocalDateTime.parse(timestampString.replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Timestamp format not recognized: " + timestampString, e2);
            }
        }
    }
}
