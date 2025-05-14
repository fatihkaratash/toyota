package com.toyota.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // If any @Scheduled tasks are used

@SpringBootApplication
// @EnableScheduling // Add if you have any @Scheduled tasks within this microservice
public class ToyotaConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToyotaConsumerApplication.class, args);
    }

}
