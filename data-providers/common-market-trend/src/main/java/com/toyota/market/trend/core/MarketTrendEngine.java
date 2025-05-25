package com.toyota.market.trend.core;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Core engine for applying market trends to rate fluctuations.
 */
public class MarketTrendEngine {
    private static final Logger logger = Logger.getLogger(MarketTrendEngine.class.getName());
    
    private final AtomicReference<TrendConfiguration> activeConfig;
    private final Random random = new Random();
    
    // Constants for trend bias calculation
    private static final double BULL_BIAS_FACTOR = 0.8;
    private static final double BEAR_BIAS_FACTOR = -0.8;
    private static final double NEUTRAL_BIAS_FACTOR = 0.1;
    
    // Variability applied to trend calculations
    private static final double VARIABILITY_FACTOR = 0.3;
    
    public MarketTrendEngine() {
        this.activeConfig = new AtomicReference<>(new TrendConfiguration());
    }
    
    public MarketTrendEngine(TrendConfiguration initialConfig) {
        this.activeConfig = new AtomicReference<>(
            initialConfig != null ? initialConfig : new TrendConfiguration()
        );
        logger.info("MarketTrendEngine initialized with: " + activeConfig.get());
    }
    
    /**
     * Updates the active trend configuration atomically.
     * @param newConfig The new configuration to apply
     */
    public void updateConfiguration(TrendConfiguration newConfig) {
        if (newConfig != null) {
            TrendConfiguration oldConfig = activeConfig.getAndSet(newConfig);
            logger.info("Market trend updated from " + oldConfig + " to " + newConfig);
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
     * Calculates the trend bias to apply to a price change.
     * This bias affects how much the price will change based on the current market trend.
     * 
     * @param basePrice The base price to calculate bias on
     * @param symbol Optional symbol name (for future per-symbol customization)
     * @return The trend bias amount to add to price change
     */
    public double calculateTrendBias(double basePrice, String symbol) {
        TrendConfiguration config = activeConfig.get();
        MarketTrendMode mode = config.getCurrentMode();
        double strength = config.getCurrentStrength();
        
        // No bias (or very minimal) for neutral mode or zero strength
        if (mode == MarketTrendMode.NEUTRAL || strength <= 0.01) {
            return calculateNeutralBias(basePrice);
        }
        
        // Calculate random factor to add variability
        double randomFactor = 1.0 + ((random.nextDouble() * 2 - 1) * VARIABILITY_FACTOR);
        
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
                // Should not reach here as we handled NEUTRAL above
                modeFactor = 0;
        }
        
        // Calculate final bias: basePrice * strength * modeFactor * randomFactor * scaleFactor
        double scaleFactor = 0.01; // 1% maximum impact at full strength
        double bias = basePrice * strength * modeFactor * randomFactor * scaleFactor;
        
        logger.fine("Calculated trend bias for " + symbol + ": " + bias + 
                   " (mode=" + mode + ", strength=" + strength + ")");
        
        return bias;
    }
    
    /**
     * Calculates a very small random bias for neutral markets
     */
    private double calculateNeutralBias(double basePrice) {
        // For neutral markets, apply a much smaller random bias (-0.1% to +0.1%)
        double randomFactor = (random.nextDouble() * 2 - 1) * NEUTRAL_BIAS_FACTOR;
        return basePrice * randomFactor * 0.001;
    }
    
    /**
     * Simpler version that doesn't use the symbol parameter.
     */
    public double calculateTrendBias(double basePrice) {
        return calculateTrendBias(basePrice, "UNKNOWN");
    }
}
