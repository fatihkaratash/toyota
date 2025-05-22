package com.toyota.mainapp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.aggregator.TwoWayWindowAggregator;
import com.toyota.mainapp.dto.CalculationRuleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hesaplama yapılandırmalarını yükleyen servis
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalculationConfigLoader {

    private final ObjectMapper objectMapper;
    private final TwoWayWindowAggregator aggregator;

    @Value("classpath:calculation-config.json")
    private Resource calculationConfigResource;

    /**
     * Uygulama başlangıcında yapılandırmayı yükle ve bileşenleri başlat
     */
    @PostConstruct
    public void loadConfig() {
        try {
            log.info("Hesaplama yapılandırması yükleniyor: {}", calculationConfigResource);
            
            try (InputStream inputStream = calculationConfigResource.getInputStream()) {
                JsonNode rootNode = objectMapper.readTree(inputStream);
                
                // Sembol yapılandırmalarını yükle ve toplayıcıya ayarla
                loadSymbolConfigs(rootNode);
                
                // Hesaplama kuralları da yüklenebilir (gelecekte)
            }
            
            log.info("Hesaplama yapılandırması başarıyla yüklendi");
        } catch (IOException e) {
            log.error("Hesaplama yapılandırması yüklenirken hata: {}", e.getMessage(), e);
        }
    }

    /**
     * Sembol yapılandırmalarını yükle ve toplayıcıya ayarla
     */
    private void loadSymbolConfigs(JsonNode rootNode) {
        Map<String, List<String>> symbolProvidersConfig = new HashMap<>();
        
        JsonNode symbolConfigsNode = rootNode.path("symbolConfigs");
        if (symbolConfigsNode.isArray()) {
            for (JsonNode symbolConfigNode : symbolConfigsNode) {
                String baseSymbol = symbolConfigNode.path("baseSymbol").asText();
                List<String> expectedProviders = new ArrayList<>();
                
                JsonNode providersNode = symbolConfigNode.path("expectedProviders");
                if (providersNode.isArray()) {
                    for (JsonNode providerNode : providersNode) {
                        expectedProviders.add(providerNode.asText());
                    }
                }
                
                if (!baseSymbol.isEmpty() && !expectedProviders.isEmpty()) {
                    symbolProvidersConfig.put(baseSymbol, expectedProviders);
                    log.info("Sembol yapılandırması yüklendi: {} için beklenen sağlayıcılar: {}",
                            baseSymbol, String.join(", ", expectedProviders));
                }
            }
        }
        
        // Toplayıcıyı yapılandırma ile başlat
        aggregator.initialize(symbolProvidersConfig);
        log.info("Toplayıcı {} sembol yapılandırması ile başlatıldı", symbolProvidersConfig.size());
    }
}
