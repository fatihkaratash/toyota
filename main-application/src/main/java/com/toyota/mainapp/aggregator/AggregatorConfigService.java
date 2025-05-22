package com.toyota.mainapp.aggregator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AggregatorConfigService {

    @Value("${app.aggregator.config-path:classpath:calculation-config.json}")
    private String configPath;

    private final ObjectMapper objectMapper;
    private Map<String, List<String>> expectedProvidersBySymbol = new HashMap<>();

    @PostConstruct
    public void init() {
        loadConfig();
    }

    private void loadConfig() {
        try {
            Map<String, Object> config;
            
            if (configPath.startsWith("classpath:")) {
                String resourcePath = configPath.substring("classpath:".length());
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is == null) {
                        throw new IOException("Kaynak bulunamadı: " + resourcePath);
                    }
                    config = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                }
            } else {
                config = objectMapper.readValue(Files.readAllBytes(Paths.get(configPath)), 
                                               new TypeReference<Map<String, Object>>() {});
            }
            
            // Konfigürasyonu ayrıştır
            if (config.containsKey("symbolConfigs")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> symbolConfigs = (List<Map<String, Object>>) config.get("symbolConfigs");
                
                for (Map<String, Object> symbolConfig : symbolConfigs) {
                    String baseSymbol = (String) symbolConfig.get("baseSymbol");
                    @SuppressWarnings("unchecked")
                    List<String> providers = (List<String>) symbolConfig.get("expectedProviders");
                    
                    if (baseSymbol != null && providers != null && !providers.isEmpty()) {
                        expectedProvidersBySymbol.put(baseSymbol, providers);
                        log.info("{} için beklenen sağlayıcılar yüklendi: {}", baseSymbol, providers);
                    }
                }
            }
            
            log.info("Beklenen sağlayıcı konfigürasyonu yüklendi: {}", expectedProvidersBySymbol);
            
        } catch (Exception e) {
            log.error("Agregasyon konfigürasyonu yüklenirken hata: {}", e.getMessage(), e);
            // Varsayılan boş konfigürasyon kullan
            expectedProvidersBySymbol = Collections.emptyMap();
        }
    }

    public Map<String, List<String>> loadExpectedProvidersBySymbol() {
        return Collections.unmodifiableMap(expectedProvidersBySymbol);
    }
}
