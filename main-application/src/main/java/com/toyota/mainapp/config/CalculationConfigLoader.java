package com.toyota.mainapp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.aggregator.TwoWayWindowAggregator;
import com.toyota.mainapp.dto.config.CalculationRuleDto; // ADDED new import
import com.toyota.mainapp.calculator.RuleEngineService; // Import RuleEngineService
import lombok.Getter; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
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
    private final RuleEngineService ruleEngineService; // Inject RuleEngineService

    @Value("classpath:calculation-config.json")
    private Resource calculationConfigResource;

    @Getter // Yüklenen kurallara dışarıdan erişim için
    private List<CalculationRuleDto> loadedCalculationRules = Collections.emptyList();

    /**
     * Uygulama başlangıcında yapılandırmayı yükle ve bileşenleri başlat
     */
    @PostConstruct
    public void loadConfig() {
        try {
            log.info("Hesaplama yapılandırması yükleniyor: {}", calculationConfigResource.getFilename());
            
            try (InputStream inputStream = calculationConfigResource.getInputStream()) {
                JsonNode rootNode = objectMapper.readTree(inputStream);
                
                // Sembol yapılandırmalarını yükle ve toplayıcıya ayarla
                loadSymbolConfigs(rootNode);
                
                // Hesaplama kurallarını yükle
                loadCalculationRules(rootNode);
            }
            
            log.info("Hesaplama yapılandırması başarıyla yüklendi. {} kural yüklendi.", loadedCalculationRules.size());
        } catch (IOException e) {
            log.error("Hesaplama yapılandırması yüklenirken hata: {}", e.getMessage(), e);
            // Hata durumunda boş bir liste ile devam et veya uygulamayı durdur
            loadedCalculationRules = Collections.emptyList();
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
                    log.debug("Sembol yapılandırması yüklendi: {} için beklenen sağlayıcılar: {}",
                            baseSymbol, String.join(", ", expectedProviders));
                }
            }
        }
        aggregator.initialize(symbolProvidersConfig);
        log.info("Toplayıcı {} sembol yapılandırması ile başlatıldı.", symbolProvidersConfig.size());
    }

    /**
     * Hesaplama kurallarını JSON'dan yükle
     */
    private void loadCalculationRules(JsonNode rootNode) {
        JsonNode rulesNode = rootNode.path("calculationRules");
        if (rulesNode.isArray() && !rulesNode.isEmpty()) {
            try {
                // ObjectMapper'ı kullanarak doğrudan List<CalculationRuleDto>'ya dönüştür
                loadedCalculationRules = objectMapper.convertValue(
                    rulesNode, 
                    new TypeReference<List<CalculationRuleDto>>() {}
                );
                log.info("{} adet hesaplama kuralı başarıyla yüklendi.", loadedCalculationRules.size());
                for (CalculationRuleDto rule : loadedCalculationRules) {
                    log.debug("Yüklenen kural: OutputSymbol={}, Strategy={}, Implementation={}", 
                        rule.getOutputSymbol(), rule.getStrategyType(), rule.getImplementation());
                }
                // RuleEngineServiceImpl'e kuralları set et
                if (ruleEngineService != null) {
                    ruleEngineService.setCalculationRules(loadedCalculationRules);
                    log.info("Hesaplama kuralları RuleEngineService'e set edildi.");
                } else {
                    log.warn("RuleEngineService is null, cannot set calculation rules.");
                }

            } catch (IllegalArgumentException e) {
                log.error("Hesaplama kuralları CalculationRuleDto listesine dönüştürülürken hata: {}", e.getMessage(), e);
                loadedCalculationRules = Collections.emptyList();
            }
        } else {
            log.warn("calculation-config.json dosyasında 'calculationRules' dizisi bulunamadı veya boş.");
            loadedCalculationRules = Collections.emptyList();
        }
    }
}