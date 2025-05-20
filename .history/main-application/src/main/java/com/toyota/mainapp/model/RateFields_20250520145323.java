package com.toyota.mainapp.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Core fields of a currency rate, including bid, ask, and timestamp.
 * This class is used within both raw and calculated rates.
 */
public class RateFields {
    private BigDecimal bid;
    private BigDecimal ask;
    private Instant timestamp;

    /**
     * Creates a new set of rate fields.
     *
     * @param bid        The bid (buying) price
     * @param ask        The ask (selling) price
     * @param timestamp  The time when this rate was created or received
     */
    public RateFields(BigDecimal bid, BigDecimal ask, Instant timestamp) {
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
    }

    /**
     * Default constructor for serialization.
     */
    public RateFields() {
    }

    /**
     * Gets the bid (buying) price.
     *
     * @return The bid price
     */
    public BigDecimal getBid() {
        return bid;
    }

    /**
     * Sets the bid (buying) price.
     *
     * @param bid The bid price
     */
    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    /**
     * Gets the ask (selling) price.
     *
     * @return The ask price
     */
    public BigDecimal getAsk() {
        return ask;
    }

    /**
     * Sets the ask (selling) price.
     *
     * @param ask The ask price
     */
    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }

    /**
     * Gets the timestamp of this rate.
     *
     * @return The timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp of this rate.
     *
     * @param timestamp The timestamp
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Calculates the mid price (average of bid and ask).
     *
     * @return The mid price
     */
    public BigDecimal getMid() {
        if (bid == null || ask == null) {
            return null;
        }
        return bid.add(ask).divide(BigDecimal.valueOf(2), 6, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Gets the spread between ask and bid prices.
     *
     * @return The spread (ask - bid)
     */
    public BigDecimal getSpread() {
        if (bid == null || ask == null) {
            return null;
        }
        return ask.subtract(bid);
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
