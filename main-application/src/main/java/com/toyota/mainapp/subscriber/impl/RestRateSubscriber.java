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

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * REST API üzerinden kur verisi alan abone
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
    private WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final TaskExecutor subscriberTaskExecutor;

    private String baseUrl = "http://localhost:8080/api";
    private long pollIntervalMs = 1000;
    private String[] symbols = new String[0];

    // Authentication credentials - will be loaded from environment/config
    private String username;
    private String password;

    // Default constructor
    public RestRateSubscriber() {
        log.warn(
                "RestRateSubscriber created with default constructor. Dependencies (WebClient.Builder, ObjectMapper, TaskExecutor) must be set via setters or this instance may not function correctly.");
        this.webClientBuilder = null;
        this.objectMapper = new ObjectMapper(); // Fallback
        this.subscriberTaskExecutor = null;
    }

    // Constructor with WebClient.Builder, ObjectMapper, and TaskExecutor
    public RestRateSubscriber(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
            TaskExecutor subscriberTaskExecutor) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper; // Injected ObjectMapper
        this.subscriberTaskExecutor = subscriberTaskExecutor; // Injected TaskExecutor
        log.debug("RestRateSubscriber created with WebClientBuilder, ObjectMapper, and TaskExecutor");
    }

    @Override
    public void init(SubscriberConfigDto config, PlatformCallback callback) {
        this.providerName = config.getName();
        this.callback = callback;

        if (config.getConnectionConfig() != null) {
            Map<String, Object> connConfig = config.getConnectionConfig();
            this.baseUrl = SubscriberUtils.getConfigValue(connConfig, "baseUrl", "http://localhost:8080/api");
            this.pollIntervalMs = SubscriberUtils.getConfigValue(connConfig, "pollIntervalMs", 1000L);
            this.symbols = SubscriberUtils.getSymbols(connConfig, this.providerName);

            // Get authentication credentials from config first, then environment variables
            this.username = SubscriberUtils.getConfigValue(connConfig, "username",
                    System.getenv("CLIENT_REST_USERNAME"));
            this.password = SubscriberUtils.getConfigValue(connConfig, "password",
                    System.getenv("CLIENT_REST_PASSWORD"));

            if (this.username == null || this.username.isEmpty()) {
                String errorMsg = String.format(
                        "[%s] REST username ZORUNLU! Config 'username' veya CLIENT_REST_USERNAME environment variable tanımlanmalı",
                        providerName);
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            if (this.password == null || this.password.isEmpty()) {
                String errorMsg = String.format(
                        "[%s] REST password ZORUNLU! Config 'password' veya CLIENT_REST_PASSWORD environment variable tanımlanmalı",
                        providerName);
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
        }

        // Initialize WebClient with authentication
        if (webClientBuilder != null && baseUrl != null && !baseUrl.isEmpty()) {
            this.webClient = webClientBuilder
                    .baseUrl(baseUrl)
                    .filter(basicAuthenticationFilter())
                    .filter(logRequest())
                    .build();
            log.info("[{}] WebClient initialized with baseUrl: {} and authentication", providerName, baseUrl);
            log.info("[{}] REST authentication config - Username: '{}', Password configured: {}",
                    providerName, username, password != null && !password.isEmpty() && !"defaultpass".equals(password));
        } else {
            log.warn("[{}] WebClientBuilder is null or baseUrl is empty. WebClient cannot be initialized.",
                    providerName);
        }

        // Log detailed information about symbols
        log.info("[{}] Loaded symbols count: {}, symbols: {}",
                providerName, symbols.length, Arrays.toString(symbols));

        log.debug("[{}] REST Subscriber initialized with config: baseUrl={}, pollIntervalMs={}, symbols={}",
                providerName, baseUrl, pollIntervalMs, Arrays.toString(symbols));
        log.info("REST abone başlatıldı: {}", providerName);
    }

    private ExchangeFilterFunction basicAuthenticationFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // Create Basic Auth header
            String credentials = username + ":" + password;
            String base64Credentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            log.debug("[{}] Adding Basic Auth header - Username: '{}', Base64 credentials: '{}'",
                    providerName, username, base64Credentials);

            ClientRequest authorizedRequest = ClientRequest.from(clientRequest)
                    .header("Authorization", "Basic " + base64Credentials)
                    .build();

            return Mono.just(authorizedRequest);
        });
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("[{}] REST Request - Method: {}, URL: {}, Headers: {}",
                    providerName, clientRequest.method(), clientRequest.url(), clientRequest.headers());
            return Mono.just(clientRequest);
        });
    }

    public void setWebClient(WebClient webClient, Retry retry, CircuitBreaker circuitBreaker) {
        this.webClient = webClient;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;

        log.debug("[{}] WebClient set.", providerName);

        if (this.retry != null) {
            log.debug("[{}] Resilience4j Retry instance set. Name: {}, MaxAttempts: {}, WaitDuration: {}",
                    providerName, this.retry.getName(), this.retry.getRetryConfig().getMaxAttempts(),
                    this.retry.getRetryConfig().getIntervalFunction().apply(1));
        }

        if (this.circuitBreaker != null) {
            log.debug("[{}] Resilience4j CircuitBreaker instance set. Name: {}, State: {}, FailureRateThreshold: {}%",
                    providerName, this.circuitBreaker.getName(), this.circuitBreaker.getState(),
                    this.circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());
            this.circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> {
                        boolean isClosed = event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED;
                        connected.set(isClosed); // Update connected status based on circuit breaker state
                        log.info("[{}] CircuitBreaker state changed to: {}. Connected status: {}",
                                providerName, event.getStateTransition().getToState(), isClosed);
                        callback.onProviderConnectionStatus(providerName, isClosed,
                                isClosed ? "Bağlantı açık (CircuitBreaker CLOSED)"
                                        : "Bağlantı kapalı (CircuitBreaker " + event.getStateTransition().getToState()
                                                + ")");
                    });
        }
    }

    @Override
    public void connect() {
        if (this.circuitBreaker != null) {
            connected.set(this.circuitBreaker.getState() == CircuitBreaker.State.CLOSED);
        } else {
            connected.set(true);
        }

        // Make sure WebClient is initialized with authentication
        if (webClient == null && webClientBuilder != null) {
            webClient = webClientBuilder
                    .baseUrl(baseUrl)
                    .filter(basicAuthenticationFilter())
                    .filter(logRequest())
                    .build();
            log.warn("[{}] WebClient initialized during connect() call as a fallback with authentication. BaseUrl: {}",
                    providerName, baseUrl);
        } else if (webClient == null && webClientBuilder == null) {
            log.error("[{}] CRITICAL: WebClient cannot be initialized in connect() because webClientBuilder is null.",
                    providerName);
        }

        log.info("[{}] REST API 'connect' called with authentication. Connected status: {}, WebClient status: {}",
                providerName, connected.get(), (webClient != null ? "initialized" : "null"));
        callback.onProviderConnectionStatus(providerName, connected.get(),
                "REST API hazır (authentication configured)");
    }

    @Override
    public void disconnect() {
        log.info("[{}] REST bağlantısı kapatılıyor...", providerName);
        stopMainLoop();
        connected.set(false);
        callback.onProviderConnectionStatus(providerName, false, "REST bağlantısı kapatıldı");
        log.info("[{}] REST bağlantısı kapatıldı.", providerName);
    }

    @Override
    public void startMainLoop() {
        if (subscriberTaskExecutor == null) {
            log.error("[{}] TaskExecutor is null. Cannot start main polling loop.", providerName);
            running.set(false);
            return;
        }
        if (running.compareAndSet(false, true)) {
            log.info("[{}] REST ana döngüsü başlatılıyor. Poll interval: {}ms", providerName, pollIntervalMs);

            subscriberTaskExecutor.execute(() -> {
                log.info("[{}] REST poll task started.", providerName);
                while (running.get()) {
                    try {
                        if (connected.get() && symbols.length > 0) {
                            log.debug("[{}] Toplam {} sembol için REST sorgusu yapılacak", providerName,
                                    symbols.length);
                            for (String symbol : symbols) {
                                if (!running.get())
                                    break;
                                fetchRate(symbol);
                            }
                            log.debug("[{}] Tüm semboller sorgulandı, sonraki poll çevrimine kadar {}ms bekleniyor",
                                    providerName, pollIntervalMs);
                        } else {
                            if (!connected.get()) {
                                log.warn("[{}] REST poll atlandı, bağlantı yok (connected=false).", providerName);
                            }
                            if (symbols.length == 0) {
                                log.warn("[{}] REST poll atlandı, izlenecek sembol yok.", providerName);
                            }
                        }

                        Thread.sleep(pollIntervalMs);
                    } catch (InterruptedException e) {
                        log.warn("[{}] REST poll task kesintiye uğradı.", providerName, e);
                        Thread.currentThread().interrupt();
                        running.set(false);
                        break;
                    } catch (Exception e) {
                        log.error("[{}] REST sorgu döngüsünde beklenmedik hata: {}", providerName, e.getMessage(), e);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                log.info("[{}] REST poll task sonlandırılıyor.", providerName);
            });
        } else {
            log.warn("[{}] REST ana döngüsü zaten çalışıyor veya başlatılamadı.", providerName);
        }
    }

    @Override
    public void stopMainLoop() {
        log.info("[{}] REST ana döngüsü durduruluyor...", providerName);
        running.set(false); // Signal the polling loop to stop
        log.info("[{}] REST ana döngüsü durduruldu.", providerName);
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
        if (webClient == null) {
            log.warn("[{}] WebClient null, {} için kur alınamıyor.", providerName, symbol);
            return;
        }

        String requestPath = "/rates/" + symbol;
        log.debug("[{}] REST rate sorgulanıyor with authentication: {}, Base URL: {}, Path: {}",
                providerName, symbol, baseUrl, requestPath);

        try {
            Mono<String> rawJsonMono = webClient.get()
                    .uri(requestPath)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, response -> {
                        log.error("[{}] REST Provider authentication failed (401 Unauthorized) for symbol: {}",
                                providerName, symbol);
                        return Mono.error(new RuntimeException("Authentication failed - 401 Unauthorized"));
                    })
                    .bodyToMono(String.class);

            // Apply Resilience4j operators if available
            if (circuitBreaker != null) {
                log.debug("[{}] Applying circuit breaker for request: {}", providerName, symbol);
                rawJsonMono = rawJsonMono.transform(CircuitBreakerOperator.of(circuitBreaker));
            }
            if (retry != null) {
                log.debug("[{}] Applying retry for request: {}", providerName, symbol);
                rawJsonMono = rawJsonMono.transform(io.github.resilience4j.reactor.retry.RetryOperator.of(retry));
            }

            rawJsonMono.subscribe(
                    jsonResponse -> {
                        if (jsonResponse != null) {
                            log.trace("[{}] Raw JSON response for {}: {}", providerName, symbol, jsonResponse);
                            try {
                                // Parse JSON manually to handle timestamp format
                                // ObjectMapper mapper = new ObjectMapper(); // Use injected ObjectMapper
                                JsonNode rootNode = this.objectMapper.readTree(jsonResponse); // Use injected
                                                                                              // objectMapper

                                log.debug("[{}] JSON parsed successfully for {}: Node type: {}",
                                        providerName, symbol, rootNode.getNodeType());

                                ProviderRateDto rate = new ProviderRateDto();
                                rate.setSymbol(rootNode.has("symbol") ? rootNode.get("symbol").asText() : symbol);
                                rate.setBid(rootNode.has("bid") ? rootNode.get("bid").asText() : null);
                                rate.setAsk(rootNode.has("ask") ? rootNode.get("ask").asText() : null);
                                rate.setProviderName(providerName);

                                // Handle timestamp - convert ISO string to long if needed
                                if (rootNode.has("timestamp")) {
                                    JsonNode timestampNode = rootNode.get("timestamp");
                                    if (timestampNode.isTextual()) {
                                        // Parse ISO timestamp to long
                                        try {
                                            rate.setTimestamp(
                                                    Instant.parse(timestampNode.asText()).toEpochMilli());
                                        } catch (Exception e) {
                                            log.warn("[{}] ISO timestamp parsing failed, using current time: {}",
                                                    providerName, e.getMessage());
                                            rate.setTimestamp(System.currentTimeMillis());
                                        }
                                    } else if (timestampNode.isNumber()) {
                                        rate.setTimestamp(timestampNode.asLong());
                                    } else {
                                        rate.setTimestamp(System.currentTimeMillis());
                                    }
                                } else {
                                    rate.setTimestamp(System.currentTimeMillis());
                                }

                                log.info("[{}] REST kurları alındı (authenticated) - Sembol: {}, Bid: {}, Ask: {}",
                                        providerName, rate.getSymbol(), rate.getBid(), rate.getAsk());

                                // Critical point: onRateAvailable call
                                log.debug("[{}] Calling onRateAvailable with ProviderRateDto for symbol: {}",
                                        providerName, rate.getSymbol());
                                callback.onRateAvailable(providerName, rate);
                                log.debug("[{}] onRateAvailable callback completed for symbol: {}",
                                        providerName, rate.getSymbol());
                            } catch (Exception e) {
                                log.error("[{}] JSON ayrıştırma hatası: {} - JSON: {}",
                                        providerName, e.getMessage(), jsonResponse, e);
                                callback.onProviderError(providerName, "JSON ayrıştırma hatası: " + symbol, e);
                            }
                        } else {
                            log.warn("[{}] {} için REST'ten null kur verisi alındı.", providerName, symbol);
                        }
                    },
                    error -> {
                        if (error.getMessage().contains("401") || error.getMessage().contains("Unauthorized")) {
                            log.error("[{}] REST authentication error for symbol {}: {}", providerName, symbol,
                                    error.getMessage());
                        } else {
                            log.error("[{}] REST sorgu hatası: {} - URL: {}{} - Hata: {}",
                                    providerName, symbol, baseUrl, requestPath, error.getMessage(), error);
                        }
                        callback.onProviderError(providerName, "REST sorgu hatası: " + symbol, error);
                    });
        } catch (Exception e) {
            log.error("[{}] REST request oluşturulurken beklenmedik hata: {} - Symbol: {}",
                    providerName, e.getMessage(), symbol, e);
            callback.onProviderError(providerName, "REST request hatası: " + symbol, e);
        }
    }
}
