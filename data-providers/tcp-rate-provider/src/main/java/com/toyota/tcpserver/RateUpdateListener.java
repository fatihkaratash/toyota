package com.toyota.tcpserver;

/**
 * Interface for objects that want to receive rate updates
 */
public interface RateUpdateListener {
    /**
     * Called when a rate is updated
     * @param rate The updated rate
     */
    void onRateUpdate(Rate rate);
    
    /**
     * Checks if the listener is subscribed to the given rate pair
     * @param pairName The rate pair name to check
     * @return true if the listener is subscribed to this pair
     */
    boolean isSubscribedTo(String pairName);
}
