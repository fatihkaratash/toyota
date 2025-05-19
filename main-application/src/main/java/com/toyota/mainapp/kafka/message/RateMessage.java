package com.toyota.mainapp.kafka.message;

import java.math.BigDecimal;
import java.time.Instant;

public class RateMessage {
    private String symbol;
    private BigDecimal bid;
    private BigDecimal ask;
    private Instant timestamp;

    // Default constructor for Jackson deserialization if consumed elsewhere
    public RateMessage() {
    }

    public RateMessage(String symbol, BigDecimal bid, BigDecimal ask, Instant timestamp) {
        this.symbol = symbol;
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        // This format is for logging/debugging. Kafka will typically send JSON.
        // Example: PF1_USDTRY|33.60|35.90|2024-12-16T16:07:15.504
        return String.format("%s|%s|%s|%s",
                symbol,
                bid != null ? bid.toPlainString() : "null",
                ask != null ? ask.toPlainString() : "null",
                timestamp != null ? timestamp.toString() : "null");
    }
}
