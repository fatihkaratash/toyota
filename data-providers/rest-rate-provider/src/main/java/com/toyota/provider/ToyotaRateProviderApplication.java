package com.toyota.provider;

import com.toyota.provider.config.ApplicationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ApplicationProperties.class)
public class ToyotaRateProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToyotaRateProviderApplication.class, args);
    }

}
