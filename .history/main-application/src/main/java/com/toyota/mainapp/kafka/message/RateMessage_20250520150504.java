package com.toyota.mainapp.kafka.message;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Message sent to Kafka containing rate information.
 */
public class RateMessage {
    
    private String symbol;
    private BigDecimal bid;
    private BigDecimal ask;
    private Instant timestamp;
    private String correlationId;
    
    /**
     * Default constructor for serialization.
     */
    public RateMessage() {
    }
    
    /**
     * Creates a new RateMessage.
     * 
     * @param symbol The symbol of the rate
     * @param bid The bid price
     * @param ask The ask price
     * @param timestamp The timestamp of the rate
     */
    public RateMessage(String symbol, BigDecimal bid, BigDecimal ask, Instant timestamp) {
        this.symbol = symbol;
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
    }
    
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
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
    
    @Override
    public String toString() {
        return "RateMessage{" +
                "symbol='" + symbol + '\'' +
                ", bid=" + bid +
                ", ask=" + ask +
                ", timestamp=" + timestamp +
                ", correlationId='" + correlationId + '\'' +
                '}';
    }
}
