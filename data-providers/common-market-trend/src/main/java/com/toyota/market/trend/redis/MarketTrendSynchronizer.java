package com.toyota.market.trend.redis;

import com.toyota.market.trend.core.MarketTrendEngine;
import com.toyota.market.trend.core.MarketTrendMode;
import com.toyota.market.trend.core.TrendConfiguration;
import com.toyota.market.trend.exception.TrendSynchronizationException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Synchronizes market trend settings across services using Redis.
 */
public class MarketTrendSynchronizer {
    private static final Logger logger = Logger.getLogger(MarketTrendSynchronizer.class.getName());
    
    // Redis keys for trend settings
    public static final String REDIS_MODE_KEY = "market:trend:current_mode";
    public static final String REDIS_STRENGTH_KEY = "market:trend:current_strength";
    
    private final MarketTrendEngine trendEngine;
    private final RedisTrendClient redisClient;
    private final ScheduledExecutorService scheduler;
    private final TrendConfiguration fallbackConfig;
    
    private volatile boolean running = false;
    
    /**
     * Creates a synchronizer with the specified components.
     * 
     * @param trendEngine The trend engine to synchronize
     * @param redisClient Client for Redis operations
     * @param fallbackConfig Configuration to use when Redis is unavailable
     */
    public MarketTrendSynchronizer(
            MarketTrendEngine trendEngine,
            RedisTrendClient redisClient,
            TrendConfiguration fallbackConfig) {
        
        this.trendEngine = trendEngine;
        this.redisClient = redisClient;
        this.fallbackConfig = fallbackConfig != null ? fallbackConfig : new TrendConfiguration();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TrendSynchronizerThread");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("MarketTrendSynchronizer initialized with fallback config: " + this.fallbackConfig);
    }
    
    /**
     * Starts periodic synchronization with Redis.
     * 
     * @param pollIntervalSeconds Seconds between synchronization checks
     */
    public void startSynchronization(int pollIntervalSeconds) {
        if (running) {
            logger.warning("Market trend synchronizer is already running");
            return;
        }
        
        running = true;
        
        int actualInterval = Math.max(1, pollIntervalSeconds);
        scheduler.scheduleAtFixedRate(
            this::syncFromRedis, 
            0, 
            actualInterval, 
            TimeUnit.SECONDS
        );
        
        logger.info("Market trend synchronizer started with poll interval: " + actualInterval + " seconds");
    }
    
    /**
     * Synchronizes settings from Redis to the trend engine.
     * If Redis is unavailable, fallback configuration is used.
     */
    public void syncFromRedis() {
        if (!running) return;
        
        try {
            if (!redisClient.isAvailable()) {
                logger.warning("Redis is not available. Using fallback configuration.");
                trendEngine.updateConfiguration(fallbackConfig.copy());
                return;
            }
            
            // Read current values from Redis
            String modeStr = redisClient.getString(REDIS_MODE_KEY);
            String strengthStr = redisClient.getString(REDIS_STRENGTH_KEY);
            
            // Use fallback values if Redis keys don't exist
            MarketTrendMode mode = fallbackConfig.getCurrentMode();
            double strength = fallbackConfig.getCurrentStrength();
            
            // Parse mode if exists in Redis
            if (modeStr != null && !modeStr.isEmpty()) {
                try {
                    mode = MarketTrendMode.valueOf(modeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid market trend mode in Redis: " + modeStr + 
                                  ". Using fallback mode: " + mode);
                }
            } else {
                logger.fine("No market trend mode found in Redis. Using fallback: " + mode);
            }
            
            // Parse strength if exists in Redis
            if (strengthStr != null && !strengthStr.isEmpty()) {
                try {
                    strength = Double.parseDouble(strengthStr);
                    // Ensure it's within valid range
                    if (strength < 0 || strength > 1) {
                        logger.warning("Market trend strength in Redis out of range [0,1]: " + strength + 
                                      ". Clamping to valid range.");
                        strength = Math.max(0, Math.min(1, strength));
                    }
                } catch (NumberFormatException e) {
                    logger.warning("Invalid market trend strength in Redis: " + strengthStr + 
                                  ". Using fallback strength: " + strength);
                }
            } else {
                logger.fine("No market trend strength found in Redis. Using fallback: " + strength);
            }
            
            // Update engine with new configuration
            TrendConfiguration newConfig = new TrendConfiguration(mode, strength);
            trendEngine.updateConfiguration(newConfig);
            
            logger.info("Updated market trend configuration from Redis: " + newConfig);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during Redis synchronization", e);
            trendEngine.updateConfiguration(fallbackConfig.copy());
            throw new TrendSynchronizationException("Failed to synchronize from Redis", e);
        }
    }
    
    /**
     * Writes current trend settings to Redis.
     * 
     * @param config The configuration to write to Redis
     * @return true if successful, false otherwise
     */
    public boolean writeToRedis(TrendConfiguration config) {
        if (config == null) return false;
        
        try {
            if (!redisClient.isAvailable()) {
                logger.warning("Cannot write to Redis: connection not available");
                return false;
            }
            
            redisClient.setString(REDIS_MODE_KEY, config.getCurrentMode().name());
            redisClient.setString(REDIS_STRENGTH_KEY, String.valueOf(config.getCurrentStrength()));
            
            logger.info("Successfully wrote trend configuration to Redis: " + config);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to write trend configuration to Redis", e);
            return false;
        }
    }
    
    /**
     * Stops synchronization.
     */
    public void stop() {
        if (!running) return;
        
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warning("Market trend synchronizer did not terminate cleanly");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Market trend synchronizer stopped");
    }
}
