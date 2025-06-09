package com.toyota.restserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.restserver.logging.LoggingHelper;
import com.toyota.restserver.model.Rate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Toyota Financial Data Platform - Rate Configuration Loader
 * 
 * Component responsible for loading initial rate configurations from JSON files.
 * Provides thread-safe access to base rate data and supports dynamic rate
 * initialization for the Toyota financial data platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Component
public class RateConfigLoader {

    private static final LoggingHelper log = new LoggingHelper(RateConfigLoader.class);

    @Value("classpath:initial-rates.json")
    private Resource initialRatesResource;

    private final ConcurrentHashMap<String, Rate> initialRates = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadInitialRates() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream inputStream = initialRatesResource.getInputStream()) {
            List<Rate> ratesList = mapper.readValue(inputStream, new TypeReference<List<Rate>>() {});
            ratesList.forEach(rate -> {
                if (rate.getTimestamp() == null || rate.getTimestamp().isEmpty()) {
                    rate.setCurrentTimestamp();
                }
                initialRates.put(rate.getPairName(), rate);
            });
            log.info(LoggingHelper.OPERATION_LOAD_CONFIG, LoggingHelper.PLATFORM_REST,
                    "Baslangic kurlarinin " + initialRates.size() + " adedi basariyla yuklendi.");
        } catch (IOException e) {
            log.error(LoggingHelper.OPERATION_LOAD_CONFIG, LoggingHelper.PLATFORM_REST,
                    initialRatesResource.getFilename() + " dosyasindan baslangic kurlari yuklenirken hata olustu", e);
            // Application might not be usable without initial rates, consider throwing a runtime exception
        }
    }

    public Map<String, Rate> getInitialRates() {
        return initialRates.entrySet().stream()
                .collect(Collectors.toConcurrentMap(Map.Entry::getKey, e -> e.getValue().copy()));
    }

    public Rate getInitialRate(String pairName) {
        Rate rate = initialRates.get(pairName);
        return (rate != null) ? rate.copy() : null;
    }
}
