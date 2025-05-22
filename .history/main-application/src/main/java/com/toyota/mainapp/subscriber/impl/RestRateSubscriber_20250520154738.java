package com.toyota.mainapp.subscriber.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.config.SubscriberProperties;
import com.toyota.mainapp.logging.LoggingHelper;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.model.RateStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RestRateSubscriber extends AbstractRateSubscriber {

    private final SubscriberProperties.RestSubscriberProperties properties;
    
    private ScheduledExecutorService scheduler;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private Instant circuitResetTime = null;
    
    private final Map<String, Instant> lastSuccessfulPollTimes = new ConcurrentHashMap<>();
    
    @Autowired
    public RestRateSubscriber(SubscriberProperties subscriberProperties, ObjectMapper objectMapper) {
        super();
        this.properties = subscriberProperties.getRest();
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }
    
    public RestRateSubscriber(RestTemplate restTemplate, ObjectMapper objectMapper, SubscriberProperties subscriberProperties) {
        super();
        this.properties = subscriberProperties.getRest();
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    public RestRateSubscriber() {
        // Default constructor for when not managed by Spring
        super();
        this.properties = new SubscriberProperties.RestSubscriberProperties();
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    protected String getSubscriberType() {
        return "REST Abonesi";
    }

    @Override
    protected void initializeResources() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rest-subscriber-" + definition.getName());
            t.setDaemon(true);
            return t;
        });
        configureRestTemplate();
    }
    
    private void configureRestTemplate() {
        // Configure timeouts if specified in additionalProperties
        int readTimeoutMs = getReadTimeout();
        int connectTimeoutMs = getConnectTimeout();
        
        // For simplicity this example doesn't implement the custom RestTemplateFactory
        // that would normally be used to configure these timeouts
        logger.info("{} {} için timeout ayarları: read={}ms, connect={}ms", 
                   getSubscriberType(), definition.getName(), readTimeoutMs, connectTimeoutMs);
    }

    @Override
    protected void startSubscriptionInternal() {
        resetCircuitBreaker();
        long pollInterval = definition.getPollIntervalMs() != null ? 
                definition.getPollIntervalMs() : properties.getDefaultPollIntervalMs();
        scheduler.scheduleAtFixedRate(this::pollRates, 0, pollInterval, TimeUnit.MILLISECONDS);
        logger.info("{} {} her {} ms'de bir yoklamaya başladı", 
                  getSubscriberType(), definition.getName(), pollInterval);
        callback.onStatusChange(definition.getName(), "Abonelik her " + pollInterval + " ms'de bir yoklamaya başladı");
    }

    private void pollRates() {
        if (!active.get() || isCircuitBreakerTripped()) {
            return;
        }

        for (String symbol : definition.getSubscribedSymbols()) {
            if (!active.get()) break;
            pollSingleSymbol(symbol);
        }
    }

    private boolean isCircuitBreakerTripped() {
        if (circuitOpen.get()) {
            if (Instant.now().isAfter(circuitResetTime)) {
                logger.info("{} {}: Devre kesici sıfırlanıyor, yarı-açık duruma geçiliyor", 
                           getSubscriberType(), definition.getName());
                circuitOpen.set(false); // Half-open state
                consecutiveFailures.set(0);
                consecutiveSuccesses.set(0);
                callback.onStatusChange(definition.getName(), "Bağlantı yeniden deneniyor");
                return false; // Allow attempt in half-open state
            } else {
                logger.debug("{} {}: Devre kesici açık, yoklama atlanıyor", 
                            getSubscriberType(), definition.getName());
                return true; // Circuit is tripped
            }
        }
        return false; // Circuit is closed or half-open and ready
    }

    private void pollSingleSymbol(String symbol) {
        LoggingHelper.setCorrelationId("poll-" + definition.getName() + "-" + symbol);
        final String effectivelyFinalSymbol = symbol; // Ensure symbol is effectively final for lambdas
        try {
            String fullUrl = buildUrlForSymbol(effectivelyFinalSymbol);
            logger.debug("{} {}: Yoklanıyor {}", getSubscriberType(), definition.getName(), fullUrl);
            
            long startTime = System.currentTimeMillis();
            HttpHeaders headers = prepareHeaders();
            HttpEntity<?> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.GET, requestEntity, String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            String responseJson = response.getBody();
            if (responseJson == null) {
                handleRequestFailure(effectivelyFinalSymbol, "Null yanıt alındı");
                return;
            }
            
            logger.debug("{} {}: {} için ham JSON alındı ({}ms): {}", 
                        getSubscriberType(), definition.getName(), effectivelyFinalSymbol, responseTime, responseJson);
            
            parseRate(responseJson, effectivelyFinalSymbol).ifPresentOrElse(
                rate -> {
                    handleRequestSuccess(effectivelyFinalSymbol);
                    callback.onRateUpdate(rate);
                    lastSuccessfulPollTimes.put(effectivelyFinalSymbol, Instant.now());
                },
                () -> handleRequestFailure(effectivelyFinalSymbol, "Kur ayrıştırılamadı")
            );

        } catch (HttpStatusCodeException e) {
            logger.error("{} {}: {} yoklanirken HTTP hatası: {} - {}", 
                        getSubscriberType(), definition.getName(), effectivelyFinalSymbol, e.getStatusCode(), e.getResponseBodyAsString(), e);
            handleRequestFailure(effectivelyFinalSymbol, "HTTP hata kodu: " + e.getStatusCode());
        } catch (ResourceAccessException e) { // More specific network errors
            logger.error("{} {}: {} sembolü için kaynak erişim hatası (muhtemelen ağ sorunu)", 
                        getSubscriberType(), definition.getName(), effectivelyFinalSymbol, e);
            handleRequestFailure(effectivelyFinalSymbol, "Bağlantı hatası: " + e.getMessage());
        } catch (Exception e) { // Catch-all for other unexpected errors
            logger.error("{} {}: {} sembolü için beklenmedik yoklama hatası", 
                        getSubscriberType(), definition.getName(), effectivelyFinalSymbol, e);
            handleRequestFailure(effectivelyFinalSymbol, "Genel yoklama hatası: " + e.getMessage());
        } finally {
            LoggingHelper.clearCorrelationId();
        }
    }
    
    private void handleRequestSuccess(String symbol) {
        int failures = consecutiveFailures.get();
        if (failures > 0) {
            logger.info("{} {}: Başarılı yanıt alındı, kesintisiz hata sayacı sıfırlanıyor (önceki: {})",
                       getSubscriberType(), definition.getName(), failures);
            consecutiveFailures.set(0);
        }
        
        if (circuitOpen.get()) {
            // In half-open state, count successes to determine if circuit should close
            int successes = consecutiveSuccesses.incrementAndGet();
            int threshold = getSuccessThreshold();
            logger.info("{} {}: Yarı-açık devre kesici, ardışık başarı: {}/{}", 
                       getSubscriberType(), definition.getName(), successes, threshold);
            
            if (successes >= threshold) {
                logger.info("{} {}: Devre kesici kapatılıyor (servis sağlıklı)", 
                           getSubscriberType(), definition.getName());
                resetCircuitBreaker();
                callback.onStatusChange(definition.getName(), "Bağlantı başarıyla yeniden sağlandı");
            }
        }
    }
    
    private void handleRequestFailure(String symbol, String reason) {
        logger.warn("{} {}: {} için başarısız yoklama: {}", 
                   getSubscriberType(), definition.getName(), symbol, reason);
        callback.onError(definition.getName(), symbol + " için yoklama hatası: " + reason, null);
        
        // Update circuit breaker state
        consecutiveSuccesses.set(0);
        int failures = consecutiveFailures.incrementAndGet();
        int threshold = getFailureThreshold();
        
        logger.debug("{} {}: Ardışık hata sayısı: {}/{}", 
                    getSubscriberType(), definition.getName(), failures, threshold);
        
        if (!circuitOpen.get() && failures >= threshold) {
            openCircuitBreaker();
            callback.onStatusChange(definition.getName(), 
                                   "Çok fazla hata nedeniyle bağlantı geçici olarak askıya alındı");
        }
    }
    
    private void openCircuitBreaker() {
        circuitOpen.set(true);
        circuitResetTime = Instant.now().plusSeconds(getCircuitTimeoutSeconds());
        logger.warn("{} {}: Devre kesici açıldı, {} saniye sonra yarı-açık duruma geçecek", 
                   getSubscriberType(), definition.getName(), getCircuitTimeoutSeconds());
    }
    
    private void resetCircuitBreaker() {
        circuitOpen.set(false);
        consecutiveFailures.set(0);
        consecutiveSuccesses.set(0);
        circuitResetTime = null;
    }
    
    private String buildUrlForSymbol(String symbol) {
        String baseUrl = definition.getUrl();
        
        // Check if URL already ends with "/"
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        
        // Check if there's a custom URL template in additionalProperties
        return findProperty("urlTemplate", String.class)
                .map(template -> template.replace("{baseUrl}", baseUrl).replace("{symbol}", symbol))
                .orElse(baseUrl + symbol);
    }
    
    private HttpHeaders prepareHeaders() {
        HttpHeaders headers = new HttpHeaders();
        
        // Add basic headers
        headers.set("User-Agent", "ToyotaRateSubscriber/1.0");
        headers.set("Accept", "application/json");
        
        // Add custom headers from additionalProperties if present
        getMapProperty("headers").forEach(headers::set);
        
        return headers;
    }

    private Optional<Rate> parseRate(String jsonResponse, String originalSymbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            // Assuming the JSON structure from README: {"rateName": "PF2_USDTRY", "bid": ..., "ask": ..., "timestamp": ...}
            String rateName = rootNode.path("rateName").asText();
            
            // Validate if rateName matches the polled symbol, or use rateName from response
            if (!originalSymbol.equals(rateName)) {
                logger.warn("{} {}: {} sembolü için yoklandı ancak rateName {} alındı", 
                            getSubscriberType(), definition.getName(), originalSymbol, rateName);
                // Use the rate name from the response if it's one of our subscribed symbols
                if (!definition.getSubscribedSymbols().contains(rateName)) {
                    logger.warn("{} {}: {} sembolü abone olunan bir sembol değil, yoksayılıyor", 
                                getSubscriberType(), definition.getName(), rateName);
                    return Optional.empty();
                }
            }
            
            // Check for required fields
            if (!rootNode.has("bid") || !rootNode.has("ask") || !rootNode.has("timestamp")) {
                logger.warn("{} {}: Yanıt gerekli alanları içermiyor: {}", 
                            getSubscriberType(), definition.getName(), jsonResponse);
                return Optional.empty();
            }

            BigDecimal bid = new BigDecimal(rootNode.path("bid").asText());
            BigDecimal ask = new BigDecimal(rootNode.path("ask").asText());
            
            // Handle different timestamp formats
            Instant timestamp;
            String timestampStr = rootNode.path("timestamp").asText();
            try {
                // Try ISO 8601 format (e.g., "2024-12-14T21:18:21.178Z")
                timestamp = OffsetDateTime.parse(timestampStr).toInstant();
            } catch (Exception e) {
                try {
                    // Try direct Instant parse
                    timestamp = Instant.parse(timestampStr);
                } catch (Exception e2) {
                    // If all parsing fails, use current time and log warning
                    logger.warn("{} {}: Zaman damgası ayrıştırılamadı: {}, şu anki zaman kullanılıyor", 
                                getSubscriberType(), definition.getName(), timestampStr);
                    timestamp = Instant.now();
                }
            }

            RateFields rateFields = new RateFields(bid, ask, timestamp);
            return Optional.of(new Rate(rateName, definition.getName(), rateFields, RateStatus.ACTIVE));
        } catch (Exception e) {
            logger.error("{} {}: {} sembolü için JSON yanıtı ayrıştırılamadı: {}", 
                         getSubscriberType(), definition.getName(), originalSymbol, jsonResponse, e);
            return Optional.empty();
        }
    }

    @Override
    protected void stopSubscriptionInternal() {
        shutdownScheduler();
    }

    private void shutdownScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Check if the circuit breaker is currently open (service considered unhealthy)
     * 
     * @return true if circuit breaker is open, false otherwise
     */
    public boolean isCircuitBreakerOpen() {
        return circuitOpen.get();
    }
    
    /**
     * Get statistics about polling success/failure
     * 
     * @return Map of symbol to last successful poll time
     */
    public Map<String, Instant> getLastSuccessfulPollTimes() {
        return lastSuccessfulPollTimes;
    }
    
    // Configuration getters with defaults
    
    private int getReadTimeout() {
        return getProperty("readTimeoutMs", properties.getReadTimeoutMs());
    }
    
    private int getConnectTimeout() {
        return getProperty("connectTimeoutMs", properties.getConnectionTimeoutMs());
    }
    
    private int getFailureThreshold() {
        return getProperty("failureThreshold", properties.getCircuitFailureThreshold());
    }
    
    private int getSuccessThreshold() {
        return getProperty("successThreshold", properties.getCircuitSuccessThreshold());
    }
    
    private int getCircuitTimeoutSeconds() {
        return getProperty("circuitTimeoutSeconds", properties.getCircuitTimeoutSeconds());
    }
}
