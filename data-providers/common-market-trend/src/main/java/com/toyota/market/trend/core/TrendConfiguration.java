package com.toyota.market.trend.core;

/**
 * Configuration parameters for market trend simulation.
 */
public class TrendConfiguration {
    private MarketTrendMode currentMode = MarketTrendMode.NEUTRAL;
    private double currentStrength = 0.5; // 0.0 to 1.0
    
    public TrendConfiguration() {
        // Default constructor
    }
    
    public TrendConfiguration(MarketTrendMode mode, double strength) {
        this.currentMode = mode != null ? mode : MarketTrendMode.NEUTRAL;
        setCurrentStrength(strength); // Use setter for validation
    }
    
    public MarketTrendMode getCurrentMode() {
        return currentMode;
    }
    
    public void setCurrentMode(MarketTrendMode currentMode) {
        this.currentMode = currentMode != null ? currentMode : MarketTrendMode.NEUTRAL;
    }
    
    public double getCurrentStrength() {
        return currentStrength;
    }
    
    public void setCurrentStrength(double currentStrength) {
        // Ensure strength is between 0.0 and 1.0
        if (currentStrength < 0.0) {
            this.currentStrength = 0.0;
        } else if (currentStrength > 1.0) {
            this.currentStrength = 1.0;
        } else {
            this.currentStrength = currentStrength;
        }
    }
    
    /**
     * Creates a copy of this configuration
     * 
     * @return A new TrendConfiguration with the same values
     */
    public TrendConfiguration copy() {
        return new TrendConfiguration(this.currentMode, this.currentStrength);
    }
    
    @Override
    public String toString() {
        return "TrendConfiguration{mode=" + currentMode + 
               ", strength=" + String.format("%.2f", currentStrength) + "}";
    }
}
