package com.toyota.mainapp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.aggregator.TwoWayWindowAggregator;
import com.toyota.mainapp.dto.config.CalculationRuleDto; // ADDED new import
import com.toyota.mainapp.calculator.RuleEngineService; // Import RuleEngineService
import lombok.Getter; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // This annotation provides the 'log' variable
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
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
@Slf4j
@Component
@RequiredArgsConstructor
public class CalculationConfigLoader {

    private final ObjectMapper objectMapper;
    private final TwoWayWindowAggregator aggregator;
    private final ResourceLoader resourceLoader;
    private final RuleEngineService ruleEngineService;

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
  // CalculationConfigLoader içinde loadCalculationRules metodunu geliştirme
private void loadCalculationRules(JsonNode rootNode) {
    JsonNode rulesNode = rootNode.path("calculationRules");
    if (rulesNode.isArray() && !rulesNode.isEmpty()) {
        try {
            // Log öncesi başlangıç
            log.info("RULES-LOADING: {} adet hesaplama kuralı yükleniyor...", rulesNode.size());
            
            // ObjectMapper'ı kullanarak doğrudan List<CalculationRuleDto>'ya dönüştür
            loadedCalculationRules = objectMapper.convertValue(
                rulesNode, 
                new TypeReference<List<CalculationRuleDto>>() {}
            );
            // Her kural için kısa log ve Groovy script kontrolü
            for (CalculationRuleDto rule : loadedCalculationRules) {
                log.info("RULE-LOADED: Çıktı={}, Strateji={}, Uygulama={}",
                    rule.getOutputSymbol(),
                    rule.getStrategyType(),
                    rule.getImplementation());

                if ("groovyScriptCalculationStrategy".equals(rule.getStrategyType())) {
                    try {
                        Resource scriptResource = resourceLoader.getResource("classpath:" + rule.getImplementation());
                        if (!scriptResource.exists()) {
                            log.error("SCRIPT-MISSING: '{}' betiği bulunamadı!", rule.getImplementation());
                        }
                    } catch (Exception e) {
                        log.error("SCRIPT-ERROR: '{}' kontrol edilirken hata: {}", 
                            rule.getImplementation(), e.getMessage());
                    }
                }
            }
            
            // RuleEngineService'e kuralları set et
            if (ruleEngineService != null) {
                ruleEngineService.setCalculationRules(loadedCalculationRules);
                log.info("RULES-SET: {} adet hesaplama kuralı RuleEngineService'e başarıyla set edildi.", 
                    loadedCalculationRules.size());
            } else {
                log.warn("RULES-ERROR: RuleEngineService null, kurallar set edilemedi!");
            }
        } catch (Exception e) {
            log.error("RULES-PARSE-ERROR: Hesaplama kurallarını yüklerken hata: {}", e.getMessage(), e);
            log.error("Detaylı hata:", e);
            loadedCalculationRules = Collections.emptyList();
        }
    } else {
        log.warn("RULES-MISSING: 'calculationRules' yapılandırması bulunamadı veya boş!");
        loadedCalculationRules = Collections.emptyList();
    }
}
}