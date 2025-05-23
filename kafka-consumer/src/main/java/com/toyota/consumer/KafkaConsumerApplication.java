package com.toyota.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.scheduling.annotation.EnableScheduling; // If any @Scheduled tasks are used

@SpringBootApplication
@EnableElasticsearchRepositories(basePackages = "com.toyota.consumer.repository.opensearch")
// @EnableScheduling // Add if you have any @Scheduled tasks within this microservice
public class KafkaConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaConsumerApplication.class, args);
    }

}
