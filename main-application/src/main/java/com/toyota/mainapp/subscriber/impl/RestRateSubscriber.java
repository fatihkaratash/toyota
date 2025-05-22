package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.ProviderRateDto;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.api.SubscriberConfigDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private Thread pollThread;
    
    private WebClient webClient;
    private Retry retry;
    private CircuitBreaker circuitBreaker;
    private WebClient.Builder webClientBuilder;
    
    private String baseUrl = "http://localhost:8080/api"; // Default REST port is 8080
    private long pollIntervalMs = 10000;
    private String[] symbols = new String[0];

    // Add default constructor
    public RestRateSubscriber() {
        log.debug("RestRateSubscriber created with default constructor");
    }
    
    // Add constructor with WebClient.Builder
    public RestRateSubscriber(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
        log.debug("RestRateSubscriber created with WebClientBuilder");
    }

    @Override
    public void init(SubscriberConfigDto config, PlatformCallback callback) {
        this.providerName = config.getName();
        this.callback = callback;
        
        if (config.getConnectionConfig() != null) {
            Map<String, Object> connConfig = config.getConnectionConfig();
            this.baseUrl = getConfigValue(connConfig, "baseUrl", "http://localhost:8080/api"); // Default REST port
            this.pollIntervalMs = getConfigValue(connConfig, "pollInterval", 10000L);
            this.symbols = getSymbols(connConfig);
        }
        
        // Initialize WebClient if builder is available
        if (webClientBuilder != null && baseUrl != null && !baseUrl.isEmpty()) {
            this.webClient = webClientBuilder.baseUrl(baseUrl).build();
            log.info("[{}] WebClient initialized with baseUrl: {}", providerName, baseUrl);
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
            log.info("[{}] WebClient initialized during connect() call with baseUrl: {}", providerName, baseUrl);
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
        if (running.compareAndSet(false, true)) {
            log.info("[{}] REST ana döngüsü başlatılıyor. Poll interval: {}ms", providerName, pollIntervalMs);
            pollThread = new Thread(() -> {
                log.info("[{}] REST poll thread'i başlatıldı.", providerName);
                while (running.get()) {
                    try {
                        log.info("[{}] REST poll döngüsü başladı.", providerName);
                        if (connected.get() && symbols.length > 0) {
                            for (String symbol : symbols) {
                                if (!running.get()) break; // Check running status before fetching each symbol
                                fetchRate(symbol);
                            }
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
                        log.warn("[{}] REST poll thread'i kesintiye uğradı.", providerName, e);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("[{}] REST sorgu döngüsünde beklenmedik hata: {}", providerName, e.getMessage(), e);
                    }
                }
                log.info("[{}] REST poll thread'i sonlandırılıyor.", providerName);
            });
            
            pollThread.setName("RestPoll-" + providerName);
            pollThread.setDaemon(true);
            pollThread.start();
        } else {
            log.warn("[{}] REST ana döngüsü zaten çalışıyor veya başlatılamadı.", providerName);
        }
    }

    @Override
    public void stopMainLoop() {
        log.info("[{}] REST ana döngüsü durduruluyor...", providerName);
        if (running.compareAndSet(true, false) && pollThread != null) {
            pollThread.interrupt();
            log.info("[{}] REST poll thread'i kesme isteği gönderildi.", providerName);
        }
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

    String requestUrl = baseUrl + "/rates/" + symbol;
    log.debug("[{}] REST rate sorgulanıyor: {}, URL: {}", providerName, symbol, requestUrl);
    
    Mono<String> rawJsonMono = webClient.get()
        .uri(requestUrl)
        .retrieve()
        .bodyToMono(String.class);

    // Apply Resilience4j operators if available
    if (circuitBreaker != null) {
        rawJsonMono = rawJsonMono.transform(CircuitBreakerOperator.of(circuitBreaker));
    }
    if (retry != null) {
        rawJsonMono = rawJsonMono.transform(io.github.resilience4j.reactor.retry.RetryOperator.of(retry));
    }
        
    rawJsonMono.subscribe(
            jsonResponse -> {
                if (jsonResponse != null) {
                    try {
                        // Parse JSON manually to handle timestamp format
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode rootNode = mapper.readTree(jsonResponse);
                        
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
                        
                        log.debug("[{}] REST kurları alındı ve zenginleştirildi - Sembol: {}, ProviderRateDto: {}", 
                                 providerName, symbol, rate);
                        callback.onRateAvailable(providerName, rate);
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
                log.error("[{}] REST sorgu hatası: {} - URL: {} - Hata: {}", 
                          providerName, symbol, requestUrl, error.getMessage(), error);
                callback.onProviderError(providerName, "REST sorgu hatası: " + symbol, error);
            }
        );
}
    
    @SuppressWarnings("unchecked")
    private <T> T getConfigValue(Map<String, Object> config, String key, T defaultValue) {
        if (config == null || !config.containsKey(key)) return defaultValue;
        Object value = config.get(key);
        return (value != null && value.getClass().isAssignableFrom(defaultValue.getClass())) ? 
            (T)value : defaultValue;
    }
    
    private String[] getSymbols(Map<String, Object> config) {
        if (config == null || !config.containsKey("symbols")) {
            log.warn("[{}] Yapılandırmada 'symbols' anahtarı bulunamadı", providerName);
            return new String[0];
        }
        
        Object symbolsObj = config.get("symbols");
        log.debug("[{}] Yapılandırmadan alınan ham symbols nesnesi: {}", 
                providerName, symbolsObj);
        
        List<String> parsedSymbols = new ArrayList<>();
        
        // Handle List type (most common from JSON)
        if (symbolsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> symbolsList = (List<String>) symbolsObj;
            for (String item : symbolsList) {
                if (item != null) {
                    String[] parts = item.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            parsedSymbols.add(trimmed);
                        }
                    }
                }
            }
        } 
        // Handle simple String
        else if (symbolsObj instanceof String) {
            String symbolsStr = (String) symbolsObj;
            String[] parts = symbolsStr.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    parsedSymbols.add(trimmed);
                }
            }
        }
        // Handle String[] (less common)
        else if (symbolsObj instanceof String[]) {
            for (String s : (String[]) symbolsObj) {
                if (s != null) {
                    String[] parts = s.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            parsedSymbols.add(trimmed);
                        }
                    }
                }
            }
        }
        
        log.info("[{}] Çözümlenen semboller: {}", providerName, parsedSymbols);
        return parsedSymbols.toArray(new String[0]);
    }
}
