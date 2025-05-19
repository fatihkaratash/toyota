package com.toyota.mainapp.subscriber.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.model.RateStatus;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate; // Assuming RestTemplate will be injected or created

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RestRateSubscriber implements PlatformSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(RestRateSubscriber.class);

    private SubscriberDefinition definition;
    private PlatformCallback callback;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private RestTemplate restTemplate; // Should be configured with circuit breaker, retry, etc.
    private ObjectMapper objectMapper;


    public RestRateSubscriber() {
        // Default constructor for dynamic loading
        // In a Spring context, RestTemplate and ObjectMapper could be @Autowired
        // For now, we'll create them, but this should be improved for production
        this.restTemplate = new RestTemplate(); // Basic RestTemplate
        this.objectMapper = new ObjectMapper(); // Basic ObjectMapper
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
        logger.info("REST Abonesi {} temel URL: {} ve semboller için başlatıldı: {}",
                definition.getName(), definition.getUrl(), definition.getSubscribedSymbols());
    }

    @Override
    public void startSubscription() {
        if (active.compareAndSet(false, true)) {
            long pollInterval = definition.getPollIntervalMs() != null ? definition.getPollIntervalMs() : 5000L; // Default 5s
            scheduler.scheduleAtFixedRate(this::pollRates, 0, pollInterval, TimeUnit.MILLISECONDS);
            logger.info("REST Abonesi {} her {} ms'de bir yoklamaya başladı.", definition.getName(), pollInterval);
            callback.onStatusChange(definition.getName(), "Abonelik her " + pollInterval + " ms'de bir yoklamaya başladı.");
        } else {
            logger.warn("REST Abonesi {} zaten aktif.", definition.getName());
        }
    }

    private void pollRates() {
        if (!active.get()) return;

        for (String symbol : definition.getSubscribedSymbols()) {
            if (!active.get()) break; // Check active status before each call
            try {
                String fullUrl = definition.getUrl() + "/" + symbol; // Example: http://host/api/rates/SYMBOL
                logger.debug("REST Abonesi {}: Yoklanıyor {}", definition.getName(), fullUrl);
                
                String responseJson = restTemplate.getForObject(fullUrl, String.class);
                if (responseJson == null) {
                    logger.warn("REST Abonesi {}: {} için null yanıt alındı", definition.getName(), symbol);
                    continue;
                }
                
                logger.debug("REST Abonesi {}: {} için ham JSON alındı: {}", definition.getName(), symbol, responseJson);
                Rate rate = parseRate(responseJson, symbol);
                if (rate != null) {
                    callback.onRateUpdate(rate);
                }

            } catch (Exception e) {
                logger.error("REST Abonesi {}: {} sembolü için yoklama hatası", definition.getName(), symbol, e);
                callback.onError(definition.getName(), symbol + " için yoklama hatası: " + e.getMessage(), e);
                // Implement circuit breaker or backoff logic here if needed
            }
        }
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
                // Decide if this is an error or if rateName from response should be trusted
            }

            BigDecimal bid = new BigDecimal(rootNode.path("bid").asText());
            BigDecimal ask = new BigDecimal(rootNode.path("ask").asText());
            // Timestamp might be ISO 8601 string e.g. "2024-12-14T21:18:21.178Z"
            Instant timestamp = OffsetDateTime.parse(rootNode.path("timestamp").asText()).toInstant();

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
            logger.info("REST Abonesi {} durduruluyor...", definition.getName());
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
            logger.info("REST Abonesi {} durduruldu.", definition.getName());
            callback.onStatusChange(definition.getName(), "Abonelik durduruldu.");
        } else {
            logger.warn("REST Abonesi {} zaten durdurulmuş veya başlatılmamış.", definition.getName());
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
}
