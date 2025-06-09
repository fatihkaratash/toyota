package com.toyota.mainapp.coordinator;

import com.toyota.mainapp.coordinator.MainCoordinatorService;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/coordinator")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Coordinator Management", description = "Subscriber lifecycle and system management")
public class CoordinatorController {
    
    private final MainCoordinatorService coordinatorService;
    
    @Operation(summary = "Get all active subscribers status")
    @GetMapping("/subscribers")
    public ResponseEntity<Map<String, Object>> getActiveSubscribers() {
        // activeSubscribers map'ini expose et
        return ResponseEntity.ok(coordinatorService.getActiveSubscribersStatus());
    }
    
    @Operation(summary = "Stop specific subscriber")
    @DeleteMapping("/subscribers/{providerName}")
    public ResponseEntity<String> stopSubscriber(@PathVariable String providerName) {
        coordinatorService.stopSubscriber(providerName);
        return ResponseEntity.ok("Subscriber " + providerName + " stopped successfully");
    }
    
    @Operation(summary = "Restart subscriber")
    @PostMapping("/subscribers/{providerName}/restart")
    public ResponseEntity<String> restartSubscriber(@PathVariable String providerName) {
        coordinatorService.restartSubscriber(providerName);
        return ResponseEntity.ok("Subscriber " + providerName + " restart initiated");
    }
    
    @Operation(summary = "Get system health status")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        return ResponseEntity.ok(coordinatorService.getSystemHealthStatus());
    }
    
    @Operation(summary = "Reload subscribers configuration")
    @PostMapping("/reload-config")
    public ResponseEntity<String> reloadConfiguration() {
        coordinatorService.reloadSubscribersConfiguration();
        return ResponseEntity.ok("Configuration reload initiated");
    }

    @Operation(summary = "Subscribe to new rate symbol")
@PostMapping("/subscribers/{providerName}/subscribe/{symbol}")
public ResponseEntity<String> subscribeToSymbol(
    @PathVariable String providerName, 
    @PathVariable String symbol) {
    
    boolean success = coordinatorService.addSymbolSubscription(providerName, symbol);
    if (success) {
        return ResponseEntity.ok("Successfully subscribed to " + symbol + " on " + providerName);
    } else {
        return ResponseEntity.badRequest().body("Failed to subscribe - provider not found or not connected");
    }
}

@Operation(summary = "Unsubscribe from rate symbol")
@DeleteMapping("/subscribers/{providerName}/subscribe/{symbol}")
public ResponseEntity<String> unsubscribeFromSymbol(
    @PathVariable String providerName, 
    @PathVariable String symbol) {
    
    boolean success = coordinatorService.removeSymbolSubscription(providerName, symbol);
    return ResponseEntity.ok("Unsubscribed from " + symbol + " on " + providerName);
}

@Operation(summary = "Get current subscriptions for provider")
@GetMapping("/subscribers/{providerName}/subscriptions")
public ResponseEntity<Map<String, Object>> getProviderSubscriptions(@PathVariable String providerName) {
    return ResponseEntity.ok(coordinatorService.getProviderSubscriptions(providerName));
}

@Operation(summary = "Add new provider dynamically")
@PostMapping("/subscribers")
public ResponseEntity<String> addNewProvider(@RequestBody SubscriberConfigDto config) {
    try {
        coordinatorService.addNewProvider(config);
        return ResponseEntity.ok("Provider " + config.getName() + " added successfully");
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Failed to add provider: " + e.getMessage());
    }
    
}
}