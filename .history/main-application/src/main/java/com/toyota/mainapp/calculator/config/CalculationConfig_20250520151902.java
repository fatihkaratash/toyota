package com.toyota.mainapp.calculator.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.calculator.CalculationStrategy;
import com.toyota.mainapp.calculator.engine.GroovyScriptEngine;
import com.toyota.mainapp.calculator.engine.JavaCalculationEngine;
import com.toyota.mainapp.calculator.formula.EurTryFormula;
import com.toyota.mainapp.calculator.formula.GbpTryFormula;
import com.toyota.mainapp.calculator.formula.UsdTryFormula; // Added import
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections; // Added import
import java.util.List;
import java.util.Map;

/**
 * Configuration for rate calculation strategies and engines.
 */
@Configuration
public class CalculationConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(CalculationConfig.class);
    
    @Value("${calculation.config.path:classpath:calculation-config.json}")
    private Resource calculationConfigFile;
    
    @Value("${calculation.scripts.path:classpath:scripts/}")
    private Resource scriptsDirectory;
    
    private final ObjectMapper objectMapper;
    private List<CalculationStrategy> calculationStrategies = new ArrayList<>();
    
    @Autowired
    public CalculationConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void init() {
        try {
            // Load calculation configuration
            loadCalculationConfig();
            
            // Add built-in calculation strategies
            addBuiltInStrategies();
            
            logger.info("CalculationConfig {} strateji ile başlatıldı", calculationStrategies.size());
        } catch (Exception e) {
            logger.error("Hesaplama yapılandırması başlatılamadı: {}", e.getMessage(), e);
        }
    }
    
    @Bean
    public GroovyScriptEngine groovyScriptEngine() {
        return new GroovyScriptEngine(scriptsDirectory);
    }
    
    @Bean
    public JavaCalculationEngine javaCalculationEngine() {
        return new JavaCalculationEngine(scriptsDirectory);
    }
    
    /**
     * Provides the list of calculation strategies.
     * 
     * @return List of calculation strategies
     */
    @Bean
    public List<CalculationStrategy> getCalculationStrategies() {
        // Return an unmodifiable list to prevent external modification after initialization
        return Collections.unmodifiableList(calculationStrategies);
    }
    
    private void loadCalculationConfig() {
        if (calculationConfigFile == null || !calculationConfigFile.exists()) {
            logger.warn("Hesaplama yapılandırma dosyası belirtilmemiş veya bulunamadı: {}",
                        calculationConfigFile != null ? calculationConfigFile.getDescription() : "null");
            return;
        }

        try (InputStream inputStream = calculationConfigFile.getInputStream()) {
            Map<String, Object> config = objectMapper.readValue(
                inputStream, new TypeReference<Map<String, Object>>() {});
            
            // Process configuration to create and register calculation strategies
            if (config.containsKey("strategies")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> strategies = (List<Map<String, Object>>) config.get("strategies");
                
                for (Map<String, Object> strategyConfig : strategies) {
                    try {
                        String type = (String) strategyConfig.get("type");
                        String symbol = (String) strategyConfig.get("symbol");
                        
                        if (type == null || symbol == null) {
                            logger.warn("Geçersiz strateji yapılandırması: {}", strategyConfig);
                            continue;
                        }
                        
                        // Create appropriate calculation strategy based on type
                        switch (type.toLowerCase()) {
                            case "groovy":
                                String groovyScript = (String) strategyConfig.get("script");
                                if (groovyScript != null) {
                                    @SuppressWarnings("unchecked")
                                    List<String> sourceSymbols = (List<String>) strategyConfig.get("sourceSymbols");
                                    GroovyScriptEngine engine = groovyScriptEngine(); // Get bean
                                    engine.registerScript(symbol, groovyScript, sourceSymbols);
                                    calculationStrategies.add(engine.getStrategy(symbol));
                                    logger.info("{} için Groovy hesaplama stratejisi kaydedildi", symbol);
                                }
                                break;
                            case "java":
                                String javaClass = (String) strategyConfig.get("class");
                                if (javaClass != null) {
                                    JavaCalculationEngine engine = javaCalculationEngine(); // Get bean
                                    engine.registerClass(symbol, javaClass);
                                    calculationStrategies.add(engine.getStrategy(symbol));
                                    logger.info("{} için Java hesaplama stratejisi kaydedildi", symbol);
                                }
                                break;
                            default:
                                logger.warn("Bilinmeyen hesaplama stratejisi türü: {}", type);
                        }
                    } catch (Exception e) {
                        logger.error("Hesaplama stratejisi oluşturulurken hata: {}", e.getMessage(), e);
                    }
                }
            }
            
            logger.info("Yapılandırmadan {} hesaplama stratejisi yüklendi", calculationStrategies.size());
        } catch (IOException e) {
            logger.error("Hesaplama yapılandırma dosyası yüklenemedi: {}", calculationConfigFile.getDescription(), e);
        }
    }
    
    private void addBuiltInStrategies() {
        // Add built-in strategies
        calculationStrategies.add(new UsdTryFormula()); // Added USDTRY
        calculationStrategies.add(new EurTryFormula());
        calculationStrategies.add(new GbpTryFormula());
        
        logger.info("Dahili hesaplama stratejileri eklendi: USDTRY, EUR/TRY, GBP/TRY");
    }
}
