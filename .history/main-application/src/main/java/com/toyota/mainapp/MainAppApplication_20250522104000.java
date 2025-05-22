package com.toyota.mainapp;

import com.toyota.mainapp.coordinator.Coordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MainAppApplication {

    private static final Logger logger = LoggerFactory.getLogger(MainAppApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MainAppApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(Coordinator coordinator) {
        return args -> {
            logger.info("MainAppApplication başlatıldı. Koordinatör başlatılıyor...");
            coordinator.start();

            // Add shutdown hook to stop coordinator gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Kapatma  tetiklendi. Koordinatör durduruluyor...");
                coordinator.stop();
                logger.info("Koordinatör durduruldu.");
            }));
        };
    }
}
