package com.toyota.mainapp.model;

import java.time.Instant;

public class Rate {
    private String symbol; // e.g., PF1_USDTRY, USDTRY
    private String platformName; // Source platform if raw, or "calculated"
    private RateFields fields;
    private RateStatus status;
    private Instant receivedTimestamp; // When the main-app received/processed this rate

    public Rate(String symbol, String platformName, RateFields fields, RateStatus status) {
        this.symbol = symbol;
        this.platformName = platformName;
        this.fields = fields;
        this.status = status;
        this.receivedTimestamp = Instant.now();
    }

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getPlatformName() {
        return platformName;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    public RateFields getFields() {
        return fields;
    }

    public void setFields(RateFields fields) {
        this.fields = fields;
    }

    public RateStatus getStatus() {
        return status;
    }

    public void setStatus(RateStatus status) {
        this.status = status;
    }

    public Instant getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(Instant receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    @Override
    public String toString() {
        return "Rate{" +
                "symbol='" + symbol + '\'' +
                ", platformName='" + platformName + '\'' +
                ", fields=" + fields +
                ", status=" + status +
                ", receivedTimestamp=" + receivedTimestamp +
                '}';
    }
}
