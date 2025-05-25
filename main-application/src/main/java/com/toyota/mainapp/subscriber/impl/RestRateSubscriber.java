package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.model.ProviderRateDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.util.SubscriberUtils;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // Default constructor
    public RestRateSubscriber() {
        log.warn("RestRateSubscriber created with default constructor. Dependencies (WebClient.Builder, ObjectMapper, TaskExecutor) must be set via setters or this instance may not function correctly.");
        this.webClientBuilder = null; // Must be set
        this.objectMapper = new ObjectMapper(); // Fallback, not ideal. Prefer injected.
        this.subscriberTaskExecutor = null; // Must be set
    }
    
    // Constructor with WebClient.Builder, ObjectMapper, and TaskExecutor
    public RestRateSubscriber(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, TaskExecutor subscriberTaskExecutor) {
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
        }
        
        // Initialize WebClient if builder is available
        if (webClientBuilder != null && baseUrl != null && !baseUrl.isEmpty()) {
            this.webClient = webClientBuilder.baseUrl(baseUrl).build();
            log.info("[{}] WebClient initialized with baseUrl: {}", providerName, baseUrl);
            log.debug("[{}] WebClient created: {}", providerName, this.webClient != null);
        } else {
            log.warn("[{}] WebClientBuilder is null or baseUrl is empty. WebClient cannot be initialized.", 
                    providerName);
            if (webClientBuilder == null) {
                log.error("[{}] CRITICAL ERROR: WebClientBuilder is null! REST API calls will fail.", providerName);
            }
            if (baseUrl == null || baseUrl.isEmpty()) {
                log.error("[{}] CRITICAL ERROR: baseUrl is empty! REST API calls will fail.", providerName);
            }
        }
        
        // Log detailed information about symbols
        log.info("[{}] Loaded symbols count: {}, symbols: {}", 
                providerName, symbols.length, Arrays.toString(symbols));
        
        log.debug("[{}] REST Subscriber initialized with config: baseUrl={}, pollIntervalMs={}, symbols={}",
                providerName, baseUrl, pollIntervalMs, Arrays.toString(symbols));
        log.info("REST abone başlatıldı: {}", providerName);
    }
    
    public void setWebClient(WebClient webClient, Retry retry, CircuitBreaker circuitBreaker) {
        this.webClient = webClient;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;

        log.debug("[{}] WebClient set.", providerName);

        if (this.retry != null) {
            log.debug("[{}] Resilience4j Retry instance set. Name: {}, MaxAttempts: {}, WaitDuration: {}",
                    providerName, this.retry.getName(), this.retry.getRetryConfig().getMaxAttempts(), this.retry.getRetryConfig().getIntervalFunction().apply(1));
        }
        
        if (this.circuitBreaker != null) {
            log.debug("[{}] Resilience4j CircuitBreaker instance set. Name: {}, State: {}, FailureRateThreshold: {}%",
                    providerName, this.circuitBreaker.getName(), this.circuitBreaker.getState(), this.circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());
            this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    boolean isClosed = event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED;
                    connected.set(isClosed); // Update connected status based on circuit breaker state
                    log.info("[{}] CircuitBreaker state changed to: {}. Connected status: {}", 
                             providerName, event.getStateTransition().getToState(), isClosed);
                    callback.onProviderConnectionStatus(providerName, isClosed, 
                        isClosed ? "Bağlantı açık (CircuitBreaker CLOSED)" : "Bağlantı kapalı (CircuitBreaker " + event.getStateTransition().getToState() + ")");
                });
        }
    }

    @Override
    public void connect() {
        // For REST, "connect" might mean the initial setup is done and CB is closed.
        // Actual "connection" happens per request.
        // If CircuitBreaker is used, its initial state (CLOSED) implies connectivity.
        if (this.circuitBreaker != null) {
            connected.set(this.circuitBreaker.getState() == CircuitBreaker.State.CLOSED);
        } else {
            connected.set(true); // Assume connected if no circuit breaker
        }
        
        // Make sure WebClient is initialized
        if (webClient == null && webClientBuilder != null) {
            webClient = webClientBuilder.baseUrl(baseUrl).build();
            log.warn("[{}] WebClient initialized during connect() call as a fallback. Ideally, it should be initialized in init() or setWebClient(). BaseUrl: {}", providerName, baseUrl);
        } else if (webClient == null && webClientBuilder == null) {
            log.error("[{}] CRITICAL: WebClient cannot be initialized in connect() because webClientBuilder is null.", providerName);
        }
        
        log.info("[{}] REST API 'connect' called. Connected status: {}, WebClient status: {}", 
                providerName, connected.get(), (webClient != null ? "initialized" : "null"));
        callback.onProviderConnectionStatus(providerName, connected.get(), "REST API hazır (bağlantı durumu CB'ye bağlı)");
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
            
            subscriberTaskExecutor.execute(() -> { // Use TaskExecutor
                log.info("[{}] REST poll task started.", providerName);
                while (running.get()) {
                    try {
                        log.debug("[{}] REST poll döngüsü başladı.", providerName); // Changed to debug
                        if (connected.get() && symbols.length > 0) {
                            log.debug("[{}] Toplam {} sembol için REST sorgusu yapılacak", providerName, symbols.length); // Changed to debug
                            for (String symbol : symbols) {
                                if (!running.get()) break; 
                                log.debug("[{}] Fetching rate for symbol: {}", providerName, symbol); // Changed to debug
                                fetchRate(symbol);
                            }
                            log.debug("[{}] Tüm semboller sorgulandı, sonraki poll çevrimine kadar {}ms bekleniyor", // Changed to debug
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
                        Thread.currentThread().interrupt(); // Preserve interrupt status
                        running.set(false); // Ensure loop terminates
                        break;
                    } catch (Exception e) {
                        log.error("[{}] REST sorgu döngüsünde beklenmedik hata: {}", providerName, e.getMessage(), e);
                        // Consider a small delay here to prevent rapid-fire errors in a tight loop
                        try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
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
        // No direct thread interruption needed as TaskExecutor manages the thread.
        // The running flag will cause the loop to exit.
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
        log.debug("[{}] REST rate sorgulanıyor: {}, Base URL: {}, Path: {}", 
                 providerName, symbol, baseUrl, requestPath);
        
        try {
            Mono<String> rawJsonMono = webClient.get()
                .uri(requestPath)
                .retrieve()
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
                                JsonNode rootNode = this.objectMapper.readTree(jsonResponse); // Use injected objectMapper
                                
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
                                                Instant.parse(timestampNode.asText()).toEpochMilli()
                                            );
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
                                
                                log.info("[{}] REST kurları alındı - Sembol: {}, Bid: {}, Ask: {}", 
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
                        log.error("[{}] REST sorgu hatası: {} - URL: {}{} - Hata: {}", 
                                  providerName, symbol, baseUrl, requestPath, error.getMessage(), error);
                        callback.onProviderError(providerName, "REST sorgu hatası: " + symbol, error);
                    }
                );
        } catch (Exception e) {
            log.error("[{}] REST request oluşturulurken beklenmedik hata: {} - Symbol: {}", 
                     providerName, e.getMessage(), symbol, e);
            callback.onProviderError(providerName, "REST request hatası: " + symbol, e);
        }
    }
}
