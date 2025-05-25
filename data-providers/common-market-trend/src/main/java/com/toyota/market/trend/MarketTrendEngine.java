package com.toyota.market.trend;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core engine for applying market trends to rate fluctuations.
 */
public class MarketTrendEngine {
    private final AtomicReference<TrendConfiguration> activeConfig;
    private final Random random = new Random();
    
    // Constants for trend bias calculation
    private static final double BULL_BIAS_FACTOR = 1.0;
    private static final double BEAR_BIAS_FACTOR = -1.0;
    private static final double NEUTRAL_BIAS_FACTOR = 0.2;
    private static final double RANDOM_FACTOR = 0.3;
    
    public MarketTrendEngine() {
        this.activeConfig = new AtomicReference<>(new TrendConfiguration());
    }
    
    public MarketTrendEngine(TrendConfiguration initialConfig) {
        this.activeConfig = new AtomicReference<>(
            initialConfig != null ? initialConfig : new TrendConfiguration()
        );
    }
    
    /**
     * Updates the active trend configuration atomically.
     * @param newConfig The new configuration to apply
     */
    public void updateConfiguration(TrendConfiguration newConfig) {
        if (newConfig != null) {
            activeConfig.set(newConfig);
        }
    }
    
    /**
     * Get the current active configuration
     * @return Current trend configuration
     */
    public TrendConfiguration getActiveConfiguration() {
        return activeConfig.get();
    }
    
    /**
     * Calculates the trend bias to apply to a base price.
     * 
     * @param basePrice The base price to apply trend to
     * @param symbol Optional symbol name (for future per-symbol customization)
     * @return The trend bias amount to add to price change
     */
    public double calculateTrendBias(double basePrice, String symbol) {
        TrendConfiguration config = activeConfig.get();
        MarketTrendMode mode = config.getCurrentMode();
        double strength = config.getCurrentStrength();
        
        // No bias for neutral mode or zero strength
        if (mode == MarketTrendMode.NEUTRAL || strength <= 0.0) {
            return 0.0;
        }
        
        // Base randomization factor (introduces some variability)
        double randomFactor = (random.nextDouble() * RANDOM_FACTOR) + (1.0 - RANDOM_FACTOR);
        
        // Calculate bias based on mode
        double modeFactor;
        switch (mode) {
            case BULL:
                modeFactor = BULL_BIAS_FACTOR;
                break;
            case BEAR:
                modeFactor = BEAR_BIAS_FACTOR;
                break;
            default:
                modeFactor = NEUTRAL_BIAS_FACTOR * (random.nextDouble() - 0.5) * 2; // Small random bias
        }
        
        // Apply bias: basePrice * strength * modeFactor * randomFactor
        // The bias is proportional to the base price, trend strength, and the mode factor
        return basePrice * strength * modeFactor * randomFactor * 0.01; // Scale factor to reduce impact
    }
    
    /**
     * Simpler version that doesn't use the symbol parameter.
     */
    public double calculateTrendBias(double basePrice) {
        return calculateTrendBias(basePrice, null);
    }
}
