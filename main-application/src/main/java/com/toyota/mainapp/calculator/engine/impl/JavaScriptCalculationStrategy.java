package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.dto.model.InputRateInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import com.toyota.mainapp.util.SymbolUtils;

/**
 * JavaScript kullanan kur hesaplama stratejisi
 */
@Component("javaScriptCalculationStrategy")
@Slf4j
public class JavaScriptCalculationStrategy implements CalculationStrategy {

    @Autowired
    private ResourceLoader resourceLoader;
    
    @Autowired
    private RateCacheService rateCacheService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final ScriptEngineManager engineManager = new ScriptEngineManager();
    
     private ScriptEngine getJavaScriptEngine() {
        // Önce Graal JS motoru deneyin
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
            if (engine != null) {
                log.info("GraalVM JavaScript motoru kullanılıyor");
                return engine;
            }
        } catch (Exception e) {
            log.warn("GraalVM JavaScript motoru başlatılamadı: {}", e.getMessage());
        }
        
        // Sonra Nashorn deneyin (Java 11 ve altı için)
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            if (engine != null) {
                log.info("Nashorn JavaScript motoru kullanılıyor");
                return engine;
            }
        } catch (Exception e) {
            log.warn("Nashorn JavaScript motoru başlatılamadı: {}", e.getMessage());
        }
        
        // Son olarak Rhino deneyin (en geniş uyumluluk)
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("rhino");
            if (engine != null) {
                log.info("Rhino JavaScript motoru kullanılıyor");
                return engine;
            }
        } catch (Exception e) {
            log.warn("Rhino JavaScript motoru başlatılamadı: {}", e.getMessage());
        }
        
        // Mevcut engineManager ile son bir deneme
        ScriptEngine engine = engineManager.getEngineByName("js");
        if (engine != null) {
            log.info("Varsayılan 'js' motoru kullanılıyor");
            return engine;
        }
        
        log.error("Hiçbir JavaScript motoru bulunamadı!");
        return null;
    }

    @Override
    public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        String scriptPath = rule.getImplementation(); // e.g., "scripts/eur_try_calculator.js"
        log.info("JS-CALC-START: Kural [{}] için '{}' betiği çalıştırılıyor", 
            rule.getOutputSymbol(), scriptPath);

        try {
            // 1. Script kaynağını kontrol et
            Resource scriptResource = resourceLoader.getResource("classpath:" + scriptPath);
            if (!scriptResource.exists()) {
                log.error("JS-MISSING: '{}' betiği bulunamadı!", scriptPath);
                return Optional.empty();
            }

            // 2. Script içeriğini oku
            String scriptContent = new String(scriptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("JS-LOADED: '{}' betiği yüklendi ({} bytes)", 
                scriptPath, scriptContent.length());

            // 3. JavaScript engine'i başlat - ESKİ KOD:
            // ScriptEngine engine = engineManager.getEngineByName("nashorn");
            // YENİ KOD:
            ScriptEngine engine = getJavaScriptEngine();
            if (engine == null) {
                log.error("JS-ENGINE-ERROR: Hiçbir JavaScript motoru bulunamadı!");
                return Optional.empty();
            }
            
            // 4. Input data'yı hazırla
            Map<String, BaseRateDto> adaptedInputRates = adaptInputRatesForScript(inputRates);
            
            if (adaptedInputRates == null || adaptedInputRates.isEmpty()) {
                log.error("JS-NO-INPUTS: Script için input kurlar yok!");
                return Optional.empty();
            }
            
            // 5. Script input bilgilerini logla
            log.info("JS-RATES: {} adet giriş kuru scripte geçiliyor", adaptedInputRates.size());
            
            // 6. Script parametrelerini hazırla
            String inputRatesJson = objectMapper.writeValueAsString(adaptedInputRates);
            String outputSymbol = rule.getOutputSymbol();
            Map<String, String> parameters = rule.getInputParameters() != null ? 
                rule.getInputParameters() : Collections.emptyMap();
            
            // 7. JavaScript kodunu çalıştır
            engine.eval(scriptContent);
            engine.eval("var inputRates = " + inputRatesJson + ";");
            engine.eval("var outputSymbol = '" + outputSymbol + "';");
            
            // Parametreleri JavaScript'e geçir
            for (Map.Entry<String, String> param : parameters.entrySet()) {
                engine.eval("var " + param.getKey() + " = '" + param.getValue() + "';");
            }
            
            // Hesaplama fonksiyonunu çağır
            engine.eval("var result = calculateRate(inputRates, outputSymbol);");
            Object resultObj = engine.eval("result");
            
            if (resultObj == null) {
                log.error("JS-NULL-RESULT: Script null sonuç döndürdü");
                return Optional.empty();
            }
            
            // 8. Sonucu BaseRateDto'ya çevir
            String resultJson = objectMapper.writeValueAsString(resultObj);
            Map<String, Object> resultMap = objectMapper.readValue(resultJson, 
                    new TypeReference<Map<String, Object>>() {});
            
            if (!resultMap.containsKey("bid") || !resultMap.containsKey("ask")) {
                log.error("JS-INVALID-RESULT: Script bid/ask sonuçları döndürmedi: {}", resultMap);
                return Optional.empty();
            }
            
            BaseRateDto dto = mapToBaseRateDto(resultMap, rule);
            log.info("JS-SUCCESS: Hesaplama başarılı: {} -> bid={}, ask={}", 
                dto.getSymbol(), dto.getBid(), dto.getAsk());
            return Optional.of(dto);
            
        } catch (IOException | ScriptException e) {
            log.error("JS-ERROR: Script çalıştırılırken hata: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "javaScriptCalculationStrategy";
    }
    
    /**
     * JavaScript'ten dönen sonucu BaseRateDto'ya dönüştürür
     */
    private BaseRateDto mapToBaseRateDto(Map<String, Object> resultMap, CalculationRuleDto rule) {
        String symbol = rule.getOutputSymbol();
        
        // JSON'dan BigDecimal'a dönüş yapabilmek için
        BigDecimal bid = new BigDecimal(resultMap.get("bid").toString());
        BigDecimal ask = new BigDecimal(resultMap.get("ask").toString());
        
        BaseRateDto dto = BaseRateDto.builder()
                .symbol(symbol)
                .bid(bid)
                .ask(ask)
                .timestamp(System.currentTimeMillis())
                .rateType(RateType.CALCULATED)
                .providerName("Calculated")
                .calculatedByStrategy(getStrategyName())
                .build();
        
        // Calculation inputs varsa ekle
        if (resultMap.containsKey("calculationInputs")) {
            Object inputsObj = resultMap.get("calculationInputs");
            try {
                List<InputRateInfo> calculationInputs = objectMapper.convertValue(inputsObj, 
                    new TypeReference<List<InputRateInfo>>() {});
                dto.setCalculationInputs(calculationInputs);
            } catch (IllegalArgumentException e) {
                log.warn("Script'ten 'calculationInputs' dönüştürülemedi, kural [{}]: {}.", 
                        rule.getOutputSymbol(), e.getMessage());
                dto.setCalculationInputs(new ArrayList<>());
            }
        } else {
            dto.setCalculationInputs(new ArrayList<>());
        }
        
        return dto;
    }
    
    /**
     * Input kurlarını script'in anlayacağı formata dönüştürür
     * Hem eğik çizgili (USD/TRY) hem de eğik çizgisiz (USDTRY) formatları destekler
     */
    private Map<String, BaseRateDto> adaptInputRatesForScript(Map<String, BaseRateDto> originalInputRates) {
        Map<String, BaseRateDto> adaptedRates = new HashMap<>();
        
        for (Map.Entry<String, BaseRateDto> entry : originalInputRates.entrySet()) {
            String originalKey = entry.getKey();
            BaseRateDto rate = entry.getValue();
            
            if (originalKey == null || originalKey.isEmpty() || rate == null) {
                continue;
            }
            
            // Orjinal veriyi kopyala
            adaptedRates.put(originalKey, rate);
            
            // Her iki formatta da ekle (eğik çizgili ve eğik çizgisiz)
            if (!originalKey.contains("/")) {
                // Eğik çizgili versiyon ekle
                String slashedSymbol = SymbolUtils.formatWithSlash(originalKey);
                if (!originalKey.equals(slashedSymbol) && !adaptedRates.containsKey(slashedSymbol)) {
                    adaptedRates.put(slashedSymbol, rate);
                }
            } else {
                // Eğik çizgisiz versiyon ekle
                String unslashedSymbol = SymbolUtils.removeSlash(originalKey);
                if (!originalKey.equals(unslashedSymbol) && !adaptedRates.containsKey(unslashedSymbol)) {
                    adaptedRates.put(unslashedSymbol, rate);
                }
            }
            
            // _AVG varyantları için özel işlem
            if (originalKey.endsWith("_AVG")) {
                String baseSymbol = originalKey.substring(0, originalKey.length() - 4);
                
                // AVG olmadan ekle
                if (!adaptedRates.containsKey(baseSymbol)) {
                    adaptedRates.put(baseSymbol, rate);
                }
                
                // Hem slashed hem unslashed format için AVG olmadan ekle
                if (!baseSymbol.contains("/")) {
                    String slashedBase = SymbolUtils.formatWithSlash(baseSymbol);
                    if (!adaptedRates.containsKey(slashedBase)) {
                        adaptedRates.put(slashedBase, rate);
                    }
                } else {
                    String unslashedBase = SymbolUtils.removeSlash(baseSymbol);
                    if (!adaptedRates.containsKey(unslashedBase)) {
                        adaptedRates.put(unslashedBase, rate);
                    }
                }
            }
        }
        
        log.debug("adaptInputRatesForScript: {} orijinal sembole karşılık toplam {} sembol oluşturuldu", 
                originalInputRates.size(), adaptedRates.size());
        
        return adaptedRates;
    }
}