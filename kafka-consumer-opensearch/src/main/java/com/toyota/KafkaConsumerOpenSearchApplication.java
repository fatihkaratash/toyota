package com.toyota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class KafkaConsumerOpensearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaConsumerOpensearchApplication.class, args);
    }
}