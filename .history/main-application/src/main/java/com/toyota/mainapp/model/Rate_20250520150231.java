package com.toyota.mainapp.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for rate information from various platforms.
 */
public class Rate {
    private String symbol;
    private String platformName;
    private RateFields fields;
    private RateStatus status;
    private String rateId;
    private Instant receivedTimestamp;

    /**
     * Create a rate with all fields.
     *
     * @param symbol Platform-specific symbol (e.g., "PF1_USDTRY")
     * @param platformName Source platform name
     * @param fields Rate fields (bid, ask, timestamp)
     * @param status Status of the rate
     */
    public Rate(String symbol, String platformName, RateFields fields, RateStatus status) {
        this.symbol = symbol;
        this.platformName = platformName;
        this.fields = fields;
        this.status = status;
        this.rateId = UUID.randomUUID().toString();
        this.receivedTimestamp = Instant.now();
    }

    /**
     * Default constructor for serialization.
     */
    public Rate() {
        this.rateId = UUID.randomUUID().toString();
        this.receivedTimestamp = Instant.now();
    }

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

    public String getRateId() {
        return rateId;
    }

    public void setRateId(String rateId) {
        this.rateId = rateId;
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
                ", rateId='" + rateId + '\'' +
                ", receivedTimestamp=" + receivedTimestamp +
                '}';
    }
}
