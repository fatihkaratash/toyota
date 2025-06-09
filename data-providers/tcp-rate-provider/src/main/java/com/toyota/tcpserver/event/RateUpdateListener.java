package com.toyota.tcpserver.event;

import com.toyota.tcpserver.model.Rate;

/**
 * Toyota Financial Data Platform - Rate Update Listener Interface
 * 
 * Event listener interface for real-time rate update notifications within
 * the TCP rate provider service. Enables subscription-based rate distribution
 * with client-specific filtering capabilities.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
public interface RateUpdateListener {
    void onRateUpdate(Rate rate);
    boolean isSubscribedTo(String pairName);
}
