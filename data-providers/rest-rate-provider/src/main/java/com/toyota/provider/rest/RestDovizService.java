package com.toyota.provider.rest;

import com.toyota.provider.common.Doviz;
import com.toyota.provider.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class RestDovizService {

    private static final Logger logger = LoggerFactory.getLogger(RestDovizService.class);
    private final Map<String, Doviz> currentRates = new HashMap<>();
    private final List<String> availableRates;
    private final ApplicationProperties applicationProperties;
    private final Random random = new Random();

    public RestDovizService(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
        this.availableRates = applicationProperties.getRest().getAvailableRates();
        initializeRates();
    }

    private void initializeRates() {
        for (String rateName : availableRates) {
            currentRates.put(rateName, generateRate(rateName));
        }
    }
    
    public Doviz getRateByName(String rateName) {
        if (!availableRates.contains(rateName)) {
            logger.warn("Requested rate {} is not available via REST service.", rateName);
            return null;
        }
        // Return current in-memory rate, or generate if not present (should be by scheduler)
        return currentRates.getOrDefault(rateName, generateFreshRate(rateName));
    }

    @Scheduled(fixedRateString = "${rate-provider.rest.generate-interval-ms:2000}")
    public void scheduledRateGeneration() {
        for (String rateName : availableRates) {
            Doviz doviz = generateRate(rateName);
            currentRates.put(rateName, doviz);
        }
        logger.debug("REST Service: New currency data generated for: {}", availableRates);
    }

    public Doviz generateFreshRate(String rateName) {
        if (!availableRates.contains(rateName)) {
            logger.warn("Cannot generate fresh rate for unavailable rate: {}", rateName);
            return null;
        }
        Doviz doviz = generateRate(rateName);
        currentRates.put(rateName, doviz);
        logger.info("REST Service: Fresh rate generated for {}", rateName);
        return doviz;
    }

    private Doviz generateRate(String rateName) {
        ApplicationProperties.RateConfig config = applicationProperties.getInitialRates().get(rateName);
        double basePrice = (config != null) ? config.getBase() : getDefaultBasePrice(rateName);
        double volatility = (config != null) ? config.getVolatility() : getDefaultVolatility(rateName);
        
        double priceChange = (random.nextDouble() - 0.5) * volatility;
        double bid = basePrice + priceChange;
        double ask = bid + (bid * 0.002); // 0.2% spread

        bid = Math.max(0.0001, bid);
        ask = Math.max(bid + 0.0001, ask);

        Doviz doviz = new Doviz(rateName, bid, ask, null);
        doviz.setCurrentTimestamp();
        return doviz;
    }

    private double getDefaultBasePrice(String rateName) {
        if (rateName.contains("EURUSD")) return 1.08;
        if (rateName.contains("USDTRY")) return 33.0;
        return 1.0;
    }

    private double getDefaultVolatility(String rateName) {
        if (rateName.contains("EURUSD")) return 0.01;
        if (rateName.contains("USDTRY")) return 0.5;
        return 0.1;
    }

    public List<String> getAvailableRates() {
        return availableRates;
    }
}
