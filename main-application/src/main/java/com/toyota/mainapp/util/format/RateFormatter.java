package com.toyota.mainapp.util.format;

import com.toyota.mainapp.model.Rate;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

public class RateFormatter {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Formats a Rate object into a string similar to the Kafka message format.
     * Example: PF1_USDTRY|33.60|35.90|2024-12-16T16:07:15.504Z
     *
     * @param rate The Rate object to format.
     * @return A string representation of the rate.
     */
    public static String formatRateForLog(Rate rate) {
        if (rate == null || rate.getFields() == null) {
            return "Rate[null]";
        }
        return String.format("%s|%s|%s|%s",
                rate.getSymbol(),
                rate.getFields().getBid() != null ? rate.getFields().getBid().toPlainString() : "null",
                rate.getFields().getAsk() != null ? rate.getFields().getAsk().toPlainString() : "null",
                rate.getFields().getTimestamp() != null ? TIMESTAMP_FORMATTER.format(rate.getFields().getTimestamp()) : "null"
        );
    }

    public static String formatBigDecimal(BigDecimal value, int scale) {
        if (value == null) return "null";
        return value.setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
