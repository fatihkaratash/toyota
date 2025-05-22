package com.toyota.mainapp.subscriber;

import com.toyota.mainapp.model.Message;

public interface Subscriber {
    void onMessage(Message message);
    void start();
    void stop();
    String getName();
}
