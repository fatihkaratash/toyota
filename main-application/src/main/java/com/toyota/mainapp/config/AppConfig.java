package com.toyota.mainapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling // For features like REST polling
@EnableAsync      // For asynchronous operations
public class AppConfig {

    // General application beans can be defined here
    // For example, RestTemplate, ObjectMapper, etc.

    // Example:
    // @Bean
    // public RestTemplate restTemplate() {
    //     return new RestTemplate();
    // }
}
