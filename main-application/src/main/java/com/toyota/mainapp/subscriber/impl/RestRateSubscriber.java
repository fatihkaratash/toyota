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

import java.time.Duration;
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
    private Thread pollThread;
    
    private WebClient webClient;
    private Retry retry;
    private CircuitBreaker circuitBreaker;
    
    private String baseUrl = "http://localhost:8080"; // Default REST port is 8080
    private long pollIntervalMs = 10000;
    private String[] symbols = new String[0];

    @Override
    public void init(SubscriberConfigDto config, PlatformCallback callback) {
        this.providerName = config.getName();
        this.callback = callback;
        
        if (config.getConnectionConfig() != null) {
            Map<String, Object> connConfig = config.getConnectionConfig();
            this.baseUrl = getConfigValue(connConfig, "baseUrl", "http://localhost:8080"); // Default REST port
            this.pollIntervalMs = getConfigValue(connConfig, "pollInterval", 10000L);
            this.symbols = getSymbols(connConfig);
        }
        
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
        log.info("[{}] REST API 'connect' called. Connected status: {}", providerName, connected.get());
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
        
        Mono<ProviderRateDto> rateMono = webClient.get()
            .uri(requestUrl)
            .retrieve()
            .bodyToMono(ProviderRateDto.class);

        // Apply Resilience4j operators if available
        if (circuitBreaker != null) {
            rateMono = rateMono.transform(CircuitBreakerOperator.of(circuitBreaker));
        }
        if (retry != null) {
            rateMono = rateMono.transform(io.github.resilience4j.reactor.retry.RetryOperator.of(retry));
        }
            
        rateMono.subscribe(
                rate -> {
                    if (rate != null) {
                        log.trace("[{}] Raw ProviderRateDto received for {}: {}", providerName, symbol, rate);
                        rate.setProviderName(providerName);
                        rate.setSymbol(symbol); // Ensure symbol is correctly set, though it should come from DTO
                        rate.setTimestamp(System.currentTimeMillis()); // Enrich with received timestamp
                        log.debug("[{}] REST kurları alındı ve zenginleştirildi - Sembol: {}, ProviderRateDto: {}", 
                                 providerName, symbol, rate);
                        callback.onRateAvailable(providerName, rate);
                    } else {
                        log.warn("[{}] {} için REST'ten null kur verisi alındı.", providerName, symbol);
                    }
                },
                error -> {
                    log.error("[{}] REST sorgu hatası: {} - URL: {} - Hata: {}", 
                              providerName, symbol, requestUrl, error.getMessage(), error);
                    callback.onProviderError(providerName, "REST sorgu hatası: " + symbol, error);
                    // If circuit breaker is not used or not handling this, manually set connected to false
                    // This might be aggressive, CB should handle this.
                    // connected.set(false); 
                    // callback.onProviderConnectionStatus(providerName, false, "REST sorgu hatası: " + error.getMessage());
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
        if (config == null || !config.containsKey("symbols")) return new String[0];
        
        Object symbolsObj = config.get("symbols");
        if (symbolsObj instanceof String[]) {
            return (String[])symbolsObj;
        } else if (symbolsObj instanceof String) {
            String symbolsStr = (String) symbolsObj;
            if (symbolsStr.trim().isEmpty()) {
                return new String[0];
            }
            return Arrays.stream(symbolsStr.split(","))
                         .map(String::trim)
                         .filter(s -> !s.isEmpty())
                         .toArray(String[]::new);
        } else if (symbolsObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<String> list = (java.util.List<String>) symbolsObj;
            return list.stream()
                       .map(String::trim)
                       .filter(s -> !s.isEmpty())
                       .toArray(String[]::new);
        }
        
        log.warn("[{}] 'symbols' in connectionConfig is not in a recognized format (String, String[], or List<String>). Found: {}", 
                 providerName, symbolsObj != null ? symbolsObj.getClass().getName() : "null");
        return new String[0];
    }
}
