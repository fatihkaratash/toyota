package com.toyota.tcpserver.event;

import com.toyota.tcpserver.model.Rate;

public interface RateUpdateListener {
    void onRateUpdate(Rate rate);
    boolean isSubscribedTo(String pairName);
}
