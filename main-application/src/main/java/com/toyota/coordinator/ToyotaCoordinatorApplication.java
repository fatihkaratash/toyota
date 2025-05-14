package com.toyota.coordinator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ToyotaCoordinatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToyotaCoordinatorApplication.class, args);
    }

}
