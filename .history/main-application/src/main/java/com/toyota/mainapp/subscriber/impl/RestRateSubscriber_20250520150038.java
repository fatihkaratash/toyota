package com.toyota.mainapp.subscriber.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.model.RateStatus;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
import com.toyota.mainapp.util.LoggingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RestRateSubscriber implements PlatformSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(RestRateSubscriber.class);

    private SubscriberDefinition definition;
    private PlatformCallback callback;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    
    // Circuit breaker state
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private Instant circuitResetTime = null;
    
    // Tracks last successful poll time per symbol
    private final Map<String, Instant> lastSuccessfulPollTimes = new ConcurrentHashMap<>();
    
    // Circuit breaker settings (defaults, can be overridden from config)
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final int DEFAULT_SUCCESS_THRESHOLD = 2;
    private static final int DEFAULT_CIRCUIT_TIMEOUT_SECONDS = 60;

    // Metrics tracking
    private final Map<String, AtomicLong> messageCountsBySymbol = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCountsBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Long> responseTimesBySymbol = new ConcurrentHashMap<>();
    
    public RestRateSubscriber() {
        // Default constructor for dynamic loading
        this.restTemplate = new RestTemplate(); 
        this.objectMapper = new ObjectMapper();
    }
    
    // Constructor for manual instantiation or testing if needed
    public RestRateSubscriber(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void initialize(SubscriberDefinition definition, PlatformCallback callback) {
        this.definition = definition;
        this.callback = callback;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rest-subscriber-" + definition.getName());
            t.setDaemon(true);
            return t;
        });
        
        // Initialize metrics for each symbol
        if (definition.getSubscribedSymbols() != null) {
            for (String symbol : definition.getSubscribedSymbols()) {
                messageCountsBySymbol.put(symbol, new AtomicLong(0));
                errorCountsBySymbol.put(symbol, new AtomicLong(0));
            }
        }
        
        configureRestTemplate();
        logger.info("REST Abonesi {} temel URL: {} ve semboller için başlatıldı: {}",
                definition.getName(), definition.getUrl(), definition.getSubscribedSymbols());
    }
    
    private void configureRestTemplate() {
        // Configure timeouts if specified in additionalProperties
        if (definition.getAdditionalProperties() != null) {
            int readTimeoutMs = getReadTimeout();
            int connectTimeoutMs = getConnectTimeout();
            
            // For simplicity this example doesn't implement the custom RestTemplateFactory
            // that would normally be used to configure these timeouts
            logger.info("REST Abonesi {} için timeout ayarları: read={}ms, connect={}ms", 
                       definition.getName(), readTimeoutMs, connectTimeoutMs);
        }
    }

    @Override
    public void startSubscription() {
        if (active.compareAndSet(false, true)) {
            LoggingHelper.logStartup(logger, "REST Abonesi " + definition.getName());
            resetCircuitBreaker();
            long pollInterval = definition.getPollIntervalMs() != null ? definition.getPollIntervalMs() : 5000L; // Default 5s
            scheduler.scheduleAtFixedRate(this::pollRates, 0, pollInterval, TimeUnit.MILLISECONDS);
            logger.info("REST Abonesi {} her {} ms'de bir yoklamaya başladı", definition.getName(), pollInterval);
            callback.onStatusChange(definition.getName(), "Abonelik her " + pollInterval + " ms'de bir yoklamaya başladı");
        } else {
            LoggingHelper.logWarning(logger, "REST Abonesi " + definition.getName(), "Zaten aktif, yeniden başlatılamaz");
        }
    }

    private void pollRates() {
        if (!active.get()) return;
        
        // Check circuit breaker
        if (circuitOpen.get()) {
            if (Instant.now().isAfter(circuitResetTime)) {
                logger.info("REST Abonesi {}: Devre kesici sıfırlanıyor, yarı-açık duruma geçiliyor", 
                           definition.getName());
                // Half-open the circuit
                circuitOpen.set(false);
                consecutiveFailures.set(0);
                consecutiveSuccesses.set(0);
                callback.onStatusChange(definition.getName(), "Bağlantı yeniden deneniyor");
            } else {
                logger.debug("REST Abonesi {}: Devre kesici açık, yoklama atlanıyor", definition.getName());
                return;
            }
        }

        for (String symbol : definition.getSubscribedSymbols()) {
            if (!active.get()) break; // Check active status before each call
            
            LoggingHelper.setCorrelationId("poll-" + definition.getName() + "-" + symbol);
            try {
                String fullUrl = buildUrlForSymbol(symbol);
                logger.debug("REST Abonesi {}: Yoklanıyor {}", definition.getName(), fullUrl);
                
                // Track start time for performance monitoring
                long startTime = System.currentTimeMillis();
                
                // Prepare headers if specified in additionalProperties
                HttpHeaders headers = prepareHeaders();
                HttpEntity<?> requestEntity = new HttpEntity<>(headers);
                
                // Make the actual REST call
                ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl, HttpMethod.GET, requestEntity, String.class);
                
                // Calculate response time
                long responseTime = System.currentTimeMillis() - startTime;
                
                // Store response time metric
                responseTimesBySymbol.put(symbol, responseTime);
                
                String responseJson = response.getBody();
                if (responseJson == null) {
                    handleRequestFailure(symbol, "Null yanıt alındı");
                    continue;
                }
                
                logger.debug("REST Abonesi {}: {} için ham JSON alındı ({}ms): {}", 
                            definition.getName(), symbol, responseTime, responseJson);
                
                // Process response and update circuit breaker on success
                Rate rate = parseRate(responseJson, symbol);
                if (rate != null) {
                    handleRequestSuccess(symbol);
                    messageCountsBySymbol.get(symbol).incrementAndGet();
                    callback.onRateUpdate(rate);
                    lastSuccessfulPollTimes.put(symbol, Instant.now());
                } else {
                    handleRequestFailure(symbol, "Kur ayrıştırılamadı");
                }

            } catch (HttpStatusCodeException e) {
                logger.error("REST Abonesi {}: {} yoklanirken HTTP hatası: {} - {}", 
                            definition.getName(), symbol, e.getStatusCode(), e.getResponseBodyAsString(), e);
                handleRequestFailure(symbol, "HTTP hata kodu: " + e.getStatusCode());
                errorCountsBySymbol.get(symbol).incrementAndGet();
                
                // Handle the failure with retry logic if configured
                if (shouldRetry(symbol)) {
                    retryRequest(symbol, e);
                }
            } catch (ResourceAccessException e) {
                logger.error("REST Abonesi {}: {} sembolü için kaynak erişim hatası", 
                            definition.getName(), symbol, e);
                handleRequestFailure(symbol, "Bağlantı hatası: " + e.getMessage());
                errorCountsBySymbol.get(symbol).incrementAndGet();
                
                // Handle the failure with retry logic if configured
                if (shouldRetry(symbol)) {
                    retryRequest(symbol, e);
                }
            } catch (Exception e) {
                logger.error("REST Abonesi {}: {} sembolü için yoklama hatası", 
                            definition.getName(), symbol, e);
                handleRequestFailure(symbol, "Yoklama hatası: " + e.getMessage());
                errorCountsBySymbol.get(symbol).incrementAndGet();
            } finally {
                LoggingHelper.clearCorrelationId();
            }
        }
    }
    
    private boolean shouldRetry(String symbol) {
        int maxRetryAttempts = getMaxRetryAttempts();
        return maxRetryAttempts > 0;
    }
    
    private void retryRequest(String symbol, Exception originalException) {
        int maxRetryAttempts = getMaxRetryAttempts();
        int retryDelayMs = getRetryDelayMs();
        
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                // Wait before retry
                Thread.sleep(retryDelayMs * attempt);
                
                logger.info("REST Abonesi {}: {} sembolü için yeniden deneme {}/{}", 
                          definition.getName(), symbol, attempt, maxRetryAttempts);
                
                String fullUrl = buildUrlForSymbol(symbol);
                HttpHeaders headers = prepareHeaders();
                HttpEntity<?> requestEntity = new HttpEntity<>(headers);
                
                // Make the retry call
                ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl, HttpMethod.GET, requestEntity, String.class);
                
                String responseJson = response.getBody();
                if (responseJson != null) {
                    Rate rate = parseRate(responseJson, symbol);
                    if (rate != null) {
                        // Successful retry
                        logger.info("REST Abonesi {}: {} sembolü için yeniden deneme başarılı", 
                                  definition.getName(), symbol);
                        handleRequestSuccess(symbol);
                        callback.onRateUpdate(rate);
                        lastSuccessfulPollTimes.put(symbol, Instant.now());
                        return;
                    }
                }
                
                logger.warn("REST Abonesi {}: {} sembolü için yeniden deneme {} başarısız - geçersiz yanıt", 
                          definition.getName(), symbol, attempt);
                
            } catch (Exception e) {
                logger.warn("REST Abonesi {}: {} sembolü için yeniden deneme {} başarısız: {}", 
                          definition.getName(), symbol, attempt, e.getMessage());
                // Continue to next retry attempt
            }
        }
        
        logger.error("REST Abonesi {}: {} sembolü için tüm yeniden denemeler başarısız", 
                   definition.getName(), symbol);
    }
    
    private void handleRequestSuccess(String symbol) {
        int failures = consecutiveFailures.get();
        if (failures > 0) {
            logger.info("REST Abonesi {}: Başarılı yanıt alındı, kesintisiz hata sayacı sıfırlanıyor (önceki: {})",
                       definition.getName(), failures);
            consecutiveFailures.set(0);
        }
        
        if (circuitOpen.get()) {
            // In half-open state, count successes to determine if circuit should close
            int successes = consecutiveSuccesses.incrementAndGet();
            int threshold = getSuccessThreshold();
            logger.info("REST Abonesi {}: Yarı-açık devre kesici, ardışık başarı: {}/{}", 
                       definition.getName(), successes, threshold);
            
            if (successes >= threshold) {
                logger.info("REST Abonesi {}: Devre kesici kapatılıyor (servis sağlıklı)", 
                           definition.getName());
                resetCircuitBreaker();
                callback.onStatusChange(definition.getName(), "Bağlantı başarıyla yeniden sağlandı");
            }
        }
    }
    
    private void handleRequestFailure(String symbol, String reason) {
        logger.warn("REST Abonesi {}: {} için başarısız yoklama: {}", 
                   definition.getName(), symbol, reason);
        callback.onError(definition.getName(), symbol + " için yoklama hatası: " + reason, null);
        
        // Update circuit breaker state
        consecutiveSuccesses.set(0);
        int failures = consecutiveFailures.incrementAndGet();
        int threshold = getFailureThreshold();
        
        logger.debug("REST Abonesi {}: Ardışık hata sayısı: {}/{}", 
                    definition.getName(), failures, threshold);
        
        if (!circuitOpen.get() && failures >= threshold) {
            openCircuitBreaker();
            callback.onStatusChange(definition.getName(), 
                                   "Çok fazla hata nedeniyle bağlantı geçici olarak askıya alındı");
        }
    }
    
    private void openCircuitBreaker() {
        circuitOpen.set(true);
        circuitResetTime = Instant.now().plusSeconds(getCircuitTimeoutSeconds());
        logger.warn("REST Abonesi {}: Devre kesici açıldı, {} saniye sonra yarı-açık duruma geçecek", 
                   definition.getName(), getCircuitTimeoutSeconds());
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
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("urlTemplate")) {
            String template = (String)definition.getAdditionalProperties().get("urlTemplate");
            return template.replace("{baseUrl}", baseUrl).replace("{symbol}", symbol);
        }
        
        // Default: baseUrl/symbol
        return baseUrl + symbol;
    }
    
    private HttpHeaders prepareHeaders() {
        HttpHeaders headers = new HttpHeaders();
        
        // Add basic headers
        headers.set("User-Agent", "ToyotaRateSubscriber/1.0");
        headers.set("Accept", "application/json");
        
        // Add custom headers from additionalProperties if present
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("headers")) {
            Object headersObj = definition.getAdditionalProperties().get("headers");
            if (headersObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> customHeaders = (Map<String, String>) headersObj;
                customHeaders.forEach(headers::set);
            }
        }
        
        return headers;
    }

    private Rate parseRate(String jsonResponse, String originalSymbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            // Assuming the JSON structure from README: {"rateName": "PF2_USDTRY", "bid": ..., "ask": ..., "timestamp": ...}
            String rateName = rootNode.path("rateName").asText();
            
            // Validate if rateName matches the polled symbol, or use rateName from response
            if (!originalSymbol.equals(rateName)) {
                logger.warn("REST Abonesi {}: {} sembolü için yoklandı ancak rateName {} alındı", 
                            definition.getName(), originalSymbol, rateName);
                // Use the rate name from the response if it's one of our subscribed symbols
                if (!definition.getSubscribedSymbols().contains(rateName)) {
                    logger.warn("REST Abonesi {}: {} sembolü abone olunan bir sembol değil, yoksayılıyor", 
                                definition.getName(), rateName);
                    return null;
                }
            }
            
            // Check for required fields
            if (!rootNode.has("bid") || !rootNode.has("ask") || !rootNode.has("timestamp")) {
                logger.warn("REST Abonesi {}: Yanıt gerekli alanları içermiyor: {}", 
                            definition.getName(), jsonResponse);
                return null;
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
                    logger.warn("REST Abonesi {}: Zaman damgası ayrıştırılamadı: {}, şu anki zaman kullanılıyor", 
                                definition.getName(), timestampStr);
                    timestamp = Instant.now();
                }
            }

            RateFields rateFields = new RateFields(bid, ask, timestamp);
            return new Rate(rateName, definition.getName(), rateFields, RateStatus.ACTIVE);
        } catch (Exception e) {
            logger.error("REST Abonesi {}: {} sembolü için JSON yanıtı ayrıştırılamadı: {}", 
                         definition.getName(), originalSymbol, jsonResponse, e);
            return null;
        }
    }

    @Override
    public void stopSubscription() {
        if (active.compareAndSet(true, false)) {
            LoggingHelper.logShutdown(logger, "REST Abonesi " + definition.getName());
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
            logger.info("REST Abonesi {} durduruldu", definition.getName());
            callback.onStatusChange(definition.getName(), "Abonelik durduruldu");
        } else {
            logger.warn("REST Abonesi {} zaten durdurulmuş veya başlatılmamış", definition.getName());
        }
    }

    @Override
    public String getPlatformName() {
        return definition != null ? definition.getName() : "Başlatılmamış REST Abonesi";
    }

    @Override
    public List<String> getSubscribedSymbols() {
        return definition != null ? definition.getSubscribedSymbols() : List.of();
    }

    @Override
    public boolean isActive() {
        return active.get();
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
    
    /**
     * Gets metrics about this REST subscriber's performance
     * 
     * @return Map containing various performance metrics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("subscriberName", getPlatformName());
        metrics.put("isActive", isActive());
        metrics.put("isCircuitBreakerOpen", isCircuitBreakerOpen());
        metrics.put("subscribedSymbols", getSubscribedSymbols());
        metrics.put("lastSuccessfulPollTimes", lastSuccessfulPollTimes);
        metrics.put("messageCountsBySymbol", messageCountsBySymbol);
        metrics.put("errorCountsBySymbol", errorCountsBySymbol);
        metrics.put("responseTimesBySymbol", responseTimesBySymbol);
        metrics.put("consecutiveFailures", consecutiveFailures.get());
        metrics.put("consecutiveSuccesses", consecutiveSuccesses.get());
        return metrics;
    }
    
    // Configuration getters with defaults
    
    private int getReadTimeout() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("readTimeoutMs")) {
            return ((Number)definition.getAdditionalProperties().get("readTimeoutMs")).intValue();
        }
        return 3000; // Default 3 seconds
    }
    
    private int getConnectTimeout() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("connectTimeoutMs")) {
            return ((Number)definition.getAdditionalProperties().get("connectTimeoutMs")).intValue();
        }
        return 3000; // Default 3 seconds
    }
    
    private int getFailureThreshold() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("failureThreshold")) {
            return ((Number)definition.getAdditionalProperties().get("failureThreshold")).intValue();
        }
        return DEFAULT_FAILURE_THRESHOLD;
    }
    
    private int getSuccessThreshold() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("successThreshold")) {
            return ((Number)definition.getAdditionalProperties().get("successThreshold")).intValue();
        }
        return DEFAULT_SUCCESS_THRESHOLD;
    }
    
    private int getCircuitTimeoutSeconds() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("circuitTimeoutSeconds")) {
            return ((Number)definition.getAdditionalProperties().get("circuitTimeoutSeconds")).intValue();
        }
        return DEFAULT_CIRCUIT_TIMEOUT_SECONDS;
    }
    
    private int getMaxRetryAttempts() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("maxRetryAttempts")) {
            return ((Number)definition.getAdditionalProperties().get("maxRetryAttempts")).intValue();
        }
        return 0; // No retries by default
    }
    
    private int getRetryDelayMs() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("retryDelayMs")) {
            return ((Number)definition.getAdditionalProperties().get("retryDelayMs")).intValue();
        }
        return 1000; // Default 1 second
    }
}
