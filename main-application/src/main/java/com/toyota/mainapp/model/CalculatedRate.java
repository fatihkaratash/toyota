package com.toyota.mainapp.model;

import java.util.List;

public class CalculatedRate extends Rate {
    private String formulaId; // Identifier for the calculation formula used
    private List<String> sourceSymbols; // List of raw symbols used for calculation

    public CalculatedRate(String symbol, RateFields fields, RateStatus status, String formulaId, List<String> sourceSymbols) {
        super(symbol, "CALCULATED", fields, status); // platformName is "CALCULATED"
        this.formulaId = formulaId;
        this.sourceSymbols = sourceSymbols;
    }

    // Getters and Setters
    public String getFormulaId() {
        return formulaId;
    }

    public void setFormulaId(String formulaId) {
        this.formulaId = formulaId;
    }

    public List<String> getSourceSymbols() {
        return sourceSymbols;
    }

    public void setSourceSymbols(List<String> sourceSymbols) {
        this.sourceSymbols = sourceSymbols;
    }

    @Override
    public String toString() {
        return "CalculatedRate{" +
                "symbol='" + getSymbol() + '\'' +
                ", platformName='" + getPlatformName() + '\'' +
                ", fields=" + getFields() +
                ", status=" + getStatus() +
                ", receivedTimestamp=" + getReceivedTimestamp() +
                ", formulaId='" + formulaId + '\'' +
                ", sourceSymbols=" + sourceSymbols +
                '}';
    }
}
