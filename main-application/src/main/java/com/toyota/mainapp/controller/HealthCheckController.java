package com.toyota.mainapp.controller;

import com.toyota.mainapp.coordinator.Coordinator;
import com.toyota.mainapp.cache.RateCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthCheckController {

    private final Coordinator coordinator;
    private final RateCache rateCache;
    // Add other dependencies to check, e.g., Kafka producer status

    @Autowired
    public HealthCheckController(Coordinator coordinator, RateCache rateCache) {
        this.coordinator = coordinator;
        this.rateCache = rateCache;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> healthStatus = new HashMap<>();
        boolean overallHealthy = true;

        // Check Coordinator status
        if (coordinator != null && coordinator.isRunning()) {
            healthStatus.put("coordinator", "UP");
        } else {
            healthStatus.put("coordinator", "DOWN");
            overallHealthy = false;
        }

        // Check Cache status
        if (rateCache != null && rateCache.isAvailable()) {
            healthStatus.put("cache", "UP");
        } else {
            healthStatus.put("cache", "DOWN");
            overallHealthy = false;
        }
        
        // Add more checks for other critical components (e.g., Kafka connectivity)

        healthStatus.put("overall_status", overallHealthy ? "HEALTHY" : "UNHEALTHY");

        if (overallHealthy) {
            return ResponseEntity.ok(healthStatus);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(healthStatus);
        }
    }
}
