package com.toyota.mainapp.model;

import java.math.BigDecimal;
import java.time.Instant;

public class RateFields {
    private BigDecimal bid;
    private BigDecimal ask;
    private Instant timestamp;

    public RateFields(BigDecimal bid, BigDecimal ask, Instant timestamp) {
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
    }

    // Getters and Setters
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
        return "RateFields{" +
                "bid=" + bid +
                ", ask=" + ask +
                ", timestamp=" + timestamp +
                '}';
    }
}
