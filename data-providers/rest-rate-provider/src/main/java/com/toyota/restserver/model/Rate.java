package com.toyota.restserver.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Toyota Financial Data Platform - Rate Data Model
 *
 * Data model representing financial rate information with bid/ask prices
 * and timestamps. Supports JSON serialization and provides utility methods
 * for rate manipulation within the Toyota financial data platform.
 *
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
public class Rate {
    @JsonProperty("pairName")
    private String pairName;

    @JsonProperty("bid")
    private double bid;

    @JsonProperty("ask")
    private double ask;

    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private String timestamp;

    public Rate() {
    }

    public Rate(String pairName, double bid, double ask, String timestamp) {
        this.pairName = pairName;
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
        if (this.timestamp == null || this.timestamp.isEmpty()) {
            setCurrentTimestamp();
        }
    }

    public String getPairName() {
        return pairName;
    }

    public void setPairName(String pairName) {
        this.pairName = pairName;
    }

    public double getBid() {
        return bid;
    }

    public void setBid(double bid) {
        this.bid = bid;
    }

    public double getAsk() {
        return ask;
    }

    public void setAsk(double ask) {
        this.ask = ask;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.timestamp = sdf.format(new Date());
    }

    public Rate copy() {
        return new Rate(this.pairName, this.bid, this.ask, this.timestamp);
    }

    @Override
    public String toString() {
        return "Rate{" +
                "pairName='" + pairName + '\'' +
                ", bid=" + bid +
                ", ask=" + ask +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rate rate = (Rate) o;
        return Double.compare(rate.bid, bid) == 0 &&
                Double.compare(rate.ask, ask) == 0 &&
                Objects.equals(pairName, rate.pairName) &&
                Objects.equals(timestamp, rate.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pairName, bid, ask, timestamp);
    }
}
