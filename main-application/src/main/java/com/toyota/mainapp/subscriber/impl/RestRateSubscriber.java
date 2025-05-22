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
    
    private String baseUrl;
    private long pollIntervalMs = 10000;
    private String[] symbols = new String[0];

    @Override
    public void init(SubscriberConfigDto config, PlatformCallback callback) {
        this.providerName = config.getName();
        this.callback = callback;
        
        if (config.getConnectionConfig() != null) {
            this.baseUrl = getConfigValue(config.getConnectionConfig(), "baseUrl", "http://localhost:8080");
            this.pollIntervalMs = getConfigValue(config.getConnectionConfig(), "pollInterval", 10000L);
            this.symbols = getSymbols(config.getConnectionConfig());
        }
        
        log.info("REST abone başlatıldı: {}", providerName);
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
                        isClosed ? "Bağlantı açık" : "Bağlantı kapalı, hata oranı yüksek");
                });
        }
    }

    @Override
    public void connect() {
        connected.set(true);
        callback.onProviderConnectionStatus(providerName, true, "REST API hazır");
    }

    @Override
    public void disconnect() {
        stopMainLoop();
        connected.set(false);
        callback.onProviderConnectionStatus(providerName, false, "REST bağlantısı kapatıldı");
    }

    @Override
    public void startMainLoop() {
        if (running.compareAndSet(false, true)) {
            pollThread = new Thread(() -> {
                while (running.get()) {
                    try {
                        if (connected.get() && symbols.length > 0) {
                            for (String symbol : symbols) {
                                fetchRate(symbol);
                            }
                        }
                        
                        Thread.sleep(pollIntervalMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("REST sorgu hatası: {}", e.getMessage());
                    }
                }
            });
            
            pollThread.setName("RestPoll-" + providerName);
            pollThread.setDaemon(true);
            pollThread.start();
        }
    }

    @Override
    public void stopMainLoop() {
        if (running.compareAndSet(true, false) && pollThread != null) {
            pollThread.interrupt();
        }
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
        if (webClient == null) return;

        webClient.get()
            .uri(baseUrl + "/rates/" + symbol)
            .retrieve()
            .bodyToMono(ProviderRateDto.class)
            .subscribe(
                rate -> {
                    if (rate != null) {
                        rate.setProviderName(providerName);
                        callback.onRateAvailable(providerName, rate);
                    }
                },
                error -> callback.onProviderError(providerName, "REST sorgu hatası: " + symbol, error)
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
        
        Object symbols = config.get("symbols");
        if (symbols instanceof String[]) {
            return (String[])symbols;
        } else if (symbols instanceof String) {
            return ((String)symbols).split(",");
        }
        
        return new String[0];
    }
}
