package com.toyota.mainapp.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a calculated exchange rate that is derived from raw rates.
 * Extends the base Rate class and adds information about the calculation.
 */
public class CalculatedRate extends Rate {
    
    private String formulaId;
    private Map<String, String> sourceRateIds;
    private String calculationDetails;
    
    /**
     * Creates a new calculated rate.
     * 
     * @param symbol The rate symbol (e.g., "EURTRY")
     * @param fields The rate fields (bid, ask, timestamp)
     * @param formulaId The ID of the formula used for calculation
     * @param sourceRateIds Map of source rate symbols to provider names used in the calculation
     */
    public CalculatedRate(String symbol, RateFields fields, String formulaId, Map<String, String> sourceRateIds) {
        super(symbol, "calculated", fields, RateStatus.ACTIVE);
        this.formulaId = formulaId;
        this.sourceRateIds = new HashMap<>(sourceRateIds);
        this.calculationDetails = "";
    }
    
    /**
     * Creates a new calculated rate with calculation details.
     * 
     * @param symbol The rate symbol (e.g., "EURTRY")
     * @param fields The rate fields (bid, ask, timestamp)
     * @param formulaId The ID of the formula used for calculation
     * @param sourceRateIds Map of source rate symbols to provider names used in the calculation
     * @param calculationDetails Details about how the calculation was performed
     */
    public CalculatedRate(String symbol, RateFields fields, String formulaId, 
                          Map<String, String> sourceRateIds, String calculationDetails) {
        this(symbol, fields, formulaId, sourceRateIds);
        this.calculationDetails = calculationDetails;
    }
    
    /**
     * Gets the ID of the formula used to calculate this rate.
     * 
     * @return Formula ID
     */
    public String getFormulaId() {
        return formulaId;
    }
    
    /**
     * Sets the ID of the formula used to calculate this rate.
     * 
     * @param formulaId Formula ID
     */
    public void setFormulaId(String formulaId) {
        this.formulaId = formulaId;
    }
    
    /**
     * Gets the map of source rate symbols to provider names used in the calculation.
     * 
     * @return Unmodifiable map of source rate symbols to provider names
     */
    public Map<String, String> getSourceRateIds() {
        return Collections.unmodifiableMap(sourceRateIds);
    }
    
    /**
     * Sets the map of source rate symbols to provider names used in the calculation.
     * 
     * @param sourceRateIds Map of source rate symbols to provider names
     */
    public void setSourceRateIds(Map<String, String> sourceRateIds) {
        this.sourceRateIds = new HashMap<>(sourceRateIds);
    }
    
    /**
     * Gets details about how the calculation was performed.
     * 
     * @return Calculation details
     */
    public String getCalculationDetails() {
        return calculationDetails;
    }
    
    /**
     * Sets details about how the calculation was performed.
     * 
     * @param calculationDetails Calculation details
     */
    public void setCalculationDetails(String calculationDetails) {
        this.calculationDetails = calculationDetails;
    }
    
    @Override
    public String toString() {
        return "CalculatedRate{" +
                "symbol='" + getSymbol() + '\'' +
                ", fields=" + getFields() +
                ", status=" + getStatus() +
                ", formulaId='" + formulaId + '\'' +
                ", sourceRateIds=" + sourceRateIds +
                ", calculationDetails='" + calculationDetails + '\'' +
                ", receivedTimestamp=" + getReceivedTimestamp() +
                '}';
    }
}
