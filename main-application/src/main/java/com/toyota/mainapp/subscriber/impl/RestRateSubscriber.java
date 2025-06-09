package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.model.ProviderRateDto;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.util.SubscriberUtils;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Base64;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Toyota Financial Data Platform - REST API Rate Subscriber
 * 
 * HTTP-based rate data subscriber that polls external rate providers via REST endpoints.
 * Supports basic authentication, circuit breaker patterns, and dynamic symbol subscriptions.
 * Part of the main coordinator application for real-time financial data aggregation.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Slf4j
public class RestRateSubscriber implements PlatformSubscriber {

    private String providerName;
    private PlatformCallback callback;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private WebClient webClient;
    private Retry retry;
    private CircuitBreaker circuitBreaker;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final TaskExecutor subscriberTaskExecutor;

    private String baseUrl;
    private long pollIntervalMs;
    private String[] symbols;
    private String username;
    private String password;
    private int readTimeoutSeconds;

    private final Set<String> dynamicSubscriptions = ConcurrentHashMap.newKeySet();

    public RestRateSubscriber(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, TaskExecutor subscriberTaskExecutor) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.subscriberTaskExecutor = subscriberTaskExecutor;
    }

    @Override
    public void init(SubscriberConfigDto config, PlatformCallback callback) {
        this.providerName = config.getName();
        this.callback = callback;

        // Load environment defaults
        this.readTimeoutSeconds = getEnvInt("READ_TIMEOUT_SECONDS", 8);
        long defaultInterval = getEnvLong("REST_PROVIDER_INTERVAL_MS", 1500L);

        // Load connection config
        Map<String, Object> connConfig = config.getConnectionConfig();
        this.baseUrl = SubscriberUtils.getConfigValue(connConfig, "baseUrl", "http://localhost:8080/api");
        this.pollIntervalMs = getConfigLong(connConfig, "pollIntervalMs", defaultInterval);
        this.symbols = SubscriberUtils.getSymbols(connConfig, this.providerName);
        this.username = SubscriberUtils.getConfigValue(connConfig, "username", System.getenv("CLIENT_REST_USERNAME"));
        this.password = SubscriberUtils.getConfigValue(connConfig, "password", System.getenv("CLIENT_REST_PASSWORD"));

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("REST username and password required");
        }

        // Initialize WebClient
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .filter(basicAuthFilter())
                .build();

        log.info("[{}] REST Subscriber initialized - baseUrl: {}, pollInterval: {}ms, symbols: {}", 
                providerName, baseUrl, pollIntervalMs, symbols.length);
    }

    private ExchangeFilterFunction basicAuthFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            String credentials = username + ":" + password;
            String base64Credentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            
            ClientRequest authorizedRequest = ClientRequest.from(clientRequest)
                    .header("Authorization", "Basic " + base64Credentials)
                    .build();
            
            return Mono.just(authorizedRequest);
        });
    }

    public void setWebClient(WebClient webClient, Retry retry, CircuitBreaker circuitBreaker) {
        this.webClient = webClient;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;

        if (this.circuitBreaker != null) {
            this.circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> {
                        boolean isClosed = event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED;
                        connected.set(isClosed);
                        callback.onProviderConnectionStatus(providerName, isClosed, 
                                "CircuitBreaker " + event.getStateTransition().getToState());
                    });
        }
    }

    @Override
    public void connect() {
        connected.set(circuitBreaker == null || circuitBreaker.getState() == CircuitBreaker.State.CLOSED);
        callback.onProviderConnectionStatus(providerName, connected.get(), "REST API connected");
    }

    @Override
    public void disconnect() {
        stopMainLoop();
        connected.set(false);
        callback.onProviderConnectionStatus(providerName, false, "REST API disconnected");
    }

    @Override
    public void startMainLoop() {
        if (running.compareAndSet(false, true)) {
            subscriberTaskExecutor.execute(() -> {
                while (running.get()) {
                    try {
                        if (connected.get()) {
                            Set<String> allSymbols = getPollingSymbols();
                            for (String symbol : allSymbols) {
                                if (!running.get()) break;
                                fetchRate(symbol);
                            }
                        }
                        Thread.sleep(pollIntervalMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("[{}] Polling error: {}", providerName, e.getMessage());
                        try {
                            Thread.sleep(Math.min(pollIntervalMs, 5000L));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                running.set(false);
            });
        }
    }

    @Override
    public void stopMainLoop() {
        running.set(false);
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    private void fetchRate(String symbol) {
        if (!connected.get()) return;

        Mono<String> request = webClient.get()
                .uri("/rates/" + symbol)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(readTimeoutSeconds));

        // Apply resilience patterns
        if (circuitBreaker != null) {
            request = request.transform(CircuitBreakerOperator.of(circuitBreaker));
        }
        if (retry != null) {
            request = request.transform(io.github.resilience4j.reactor.retry.RetryOperator.of(retry));
        }

        request.subscribe(
            jsonResponse -> {
                try {
                    JsonNode rootNode = objectMapper.readTree(jsonResponse);
                    
                    ProviderRateDto rate = new ProviderRateDto();
                    rate.setSymbol(rootNode.has("symbol") ? rootNode.get("symbol").asText() : symbol);
                    rate.setBid(rootNode.has("bid") ? rootNode.get("bid").asText() : null);
                    rate.setAsk(rootNode.has("ask") ? rootNode.get("ask").asText() : null);
                    rate.setProviderName(providerName);
                    
                    // Handle timestamp
                    if (rootNode.has("timestamp")) {
                        JsonNode timestampNode = rootNode.get("timestamp");
                        if (timestampNode.isTextual()) {
                            rate.setTimestamp(Instant.parse(timestampNode.asText()).toEpochMilli());
                        } else {
                            rate.setTimestamp(timestampNode.asLong());
                        }
                    } else {
                        rate.setTimestamp(System.currentTimeMillis());
                    }

                    callback.onRateAvailable(providerName, rate);
                    
                } catch (Exception e) {
                    log.error("[{}] JSON parse error for {}: {}", providerName, symbol, e.getMessage());
                    callback.onProviderError(providerName, "Parse error: " + symbol, e);
                }
            },
            error -> {
                String errorMsg = error.getMessage();
                if (error instanceof WebClientResponseException) {
                    WebClientResponseException wcre = (WebClientResponseException) error;
                    errorMsg = String.format("HTTP %d: %s", wcre.getStatusCode().value(), wcre.getStatusText());
                }
                log.error("[{}] Fetch error for {}: {}", providerName, symbol, errorMsg);
                callback.onProviderError(providerName, "Fetch error: " + symbol, error);
            }
        );
    }

    public boolean addSymbolToPolling(String symbol) {
        if (!isConnected()) return false;
        return dynamicSubscriptions.add(symbol.toUpperCase());
    }

    public boolean removeSymbolFromPolling(String symbol) {
        return dynamicSubscriptions.remove(symbol.toUpperCase());
    }

    public Set<String> getPollingSymbols() {
        Set<String> allSymbols = new HashSet<>(Arrays.asList(symbols));
        allSymbols.addAll(dynamicSubscriptions);
        return allSymbols;
    }

    private long getConfigLong(Map<String, Object> config, String key, long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    private long getEnvLong(String envName, long defaultValue) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            try {
                return Long.parseLong(envValue.trim());
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    private int getEnvInt(String envName, int defaultValue) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }
}
