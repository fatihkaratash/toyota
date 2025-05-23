package com.toyota.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.scheduling.annotation.EnableScheduling; // If any @Scheduled tasks are used

@SpringBootApplication
//@EnableElasticsearchRepositories(basePackages = "com.toyota.consumer.repository.opensearch")
@EnableScheduling
public class KafkaConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaConsumerApplication.class, args);
    }

}
