package com.toyota.provider.common;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

public class Doviz {
    private String pair;
    private double bid;
    private double ask;
    private String timestamp; // ISO 8601 format

    public Doviz() {
    }

    public Doviz(String pair, double bid, double ask, String timestamp) {
        this.pair = pair;
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
        if (this.timestamp == null) {
            setCurrentTimestamp();
        }
    }

    public String getPair() {
        return pair;
    }

    public void setPair(String pair) {
        this.pair = pair;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Doviz doviz = (Doviz) o;
        return Double.compare(doviz.bid, bid) == 0 &&
               Double.compare(doviz.ask, ask) == 0 &&
               Objects.equals(pair, doviz.pair) &&
               Objects.equals(timestamp, doviz.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pair, bid, ask, timestamp);
    }

    @Override
    public String toString() {
        return "Doviz{" +
               "pair='" + pair + '\'' +
               ", bid=" + bid +
               ", ask=" + ask +
               ", timestamp='" + timestamp + '\'' +
               '}';
    }
}
