package com.toyota.mainapp.calculator.engine.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.InputRateInfo;
import com.toyota.mainapp.dto.model.RateType;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component("groovyScriptCalculationStrategy")
@Slf4j
public class GroovyScriptCalculationStrategy implements CalculationStrategy {

    private final ResourceLoader resourceLoader;
    private final RateCacheService rateCacheService;
    private final ObjectMapper objectMapper; // For robust conversion of calculationInputs

    public GroovyScriptCalculationStrategy(ResourceLoader resourceLoader,
                                           RateCacheService rateCacheService,
                                           ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.rateCacheService = rateCacheService;
        this.objectMapper = objectMapper;
    }

  @Override
public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
    String scriptPath = rule.getImplementation(); // e.g., "scripts/eur_try_calculator.groovy"
    log.info("GROOVY-CALC-START: Kural [{}] için '{}' betiği çalıştırılıyor", 
        rule.getOutputSymbol(), scriptPath);

    try {
        // 1. Script kaynağını kontrol et
        Resource scriptResource = resourceLoader.getResource("classpath:" + scriptPath);
        if (!scriptResource.exists()) {
            log.error("GROOVY-MISSING: '{}' betiği bulunamadı!", scriptPath);
            return Optional.empty();
        }

        // 2. Script içeriğini oku
        String scriptContent = new String(scriptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.debug("GROOVY-LOADED: '{}' betiği yüklendi ({} bytes)", 
            scriptPath, scriptContent.length());

        // 3. Script bağlamını hazırla ve değişkenleri logla
        CompilerConfiguration compilerConfig = new CompilerConfiguration();
        Binding binding = new Binding();
        binding.setVariable("cache", this.rateCacheService);
        binding.setVariable("log", log);
        binding.setVariable("outputSymbol", rule.getOutputSymbol());
        
        // 4. Input parameters'ı logla
        if (rule.getInputParameters() != null && !rule.getInputParameters().isEmpty()) {
            log.info("GROOVY-PARAMS: {} input parametresi scripte geçiliyor", 
                rule.getInputParameters().size());
            for (Map.Entry<String, String> param : rule.getInputParameters().entrySet()) {
                binding.setVariable(param.getKey(), param.getValue());
                log.debug("GROOVY-PARAM: {} = {}", param.getKey(), param.getValue());
            }
        } else {
            log.warn("GROOVY-NO-PARAMS: Script için input parametresi yok!");
        }
        
        // 5. Mevcut kurları logla
        Map<String, BaseRateDto> adaptedInputRates = adaptInputRatesForScript(inputRates);
        binding.setVariable("inputRates", adaptedInputRates);
        
        if (adaptedInputRates == null || adaptedInputRates.isEmpty()) {
            log.error("GROOVY-NO-INPUTS: Script için input kurlar yok!");
            return Optional.empty();
        }
        
        log.info("GROOVY-RATES: {} adet giriş kuru scripte geçiliyor: {}", 
            adaptedInputRates.size(),
            adaptedInputRates.keySet().stream()
                .map(k -> k + "(bid=" + adaptedInputRates.get(k).getBid() + 
                     ",ask=" + adaptedInputRates.get(k).getAsk() + ")")
                .collect(Collectors.joining(", ")));
        
        // 6. Script'i çalıştır
        GroovyShell shell = new GroovyShell(getClass().getClassLoader(), binding, compilerConfig);
        log.debug("GROOVY-EXECUTING: Script çalıştırılıyor...");
        Object result = shell.evaluate(scriptContent);
        log.debug("GROOVY-EXECUTED: Script çalıştırıldı, sonuç: {}", result);
        
        // 7. Sonucu işle
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            if (!resultMap.containsKey("bid") || !resultMap.containsKey("ask")) {
                log.error("GROOVY-INVALID-RESULT: Script bid/ask sonuçları döndürmedi: {}", resultMap);
                return Optional.empty();
            }
            
            BaseRateDto dto = mapToBaseRateDto(resultMap, rule);
            log.info("GROOVY-SUCCESS: Hesaplama başarılı: {} -> bid={}, ask={}", 
                dto.getSymbol(), dto.getBid(), dto.getAsk());
            return Optional.of(dto);
        } else {
            log.error("GROOVY-WRONG-RETURN: Script Map dönmedi, dönen tip: {}", 
                result != null ? result.getClass().getName() : "null");
            return Optional.empty();
        }
    } catch (Exception e) {
        log.error("GROOVY-ERROR: Script çalıştırılırken hata: {}", e.getMessage(), e);
        return Optional.empty();
    }
}
    @Override
    public String getStrategyName() {
        return "groovyScriptCalculationStrategy";
    }
    
    private BaseRateDto mapToBaseRateDto(Map<String, Object> resultMap, CalculationRuleDto rule) {
        BaseRateDto dto = BaseRateDto.builder()
            .rateType(RateType.CALCULATED)
            .symbol(rule.getOutputSymbol())
            .build();
        
        // Allow script to override symbol if provided
        if (resultMap.containsKey("symbol") && resultMap.get("symbol") instanceof String) {
            dto.setSymbol((String) resultMap.get("symbol"));
        }
        
        dto.setBid(convertToBigDecimal(resultMap.get("bid")));
        dto.setAsk(convertToBigDecimal(resultMap.get("ask")));
        dto.setTimestamp(convertToLong(resultMap.get("timestamp"), System.currentTimeMillis()));

        Object inputsObj = resultMap.get("calculationInputs");
        if (inputsObj != null) {
            try {
                // Use ObjectMapper for robust conversion of List<InputRateInfo>
                List<InputRateInfo> calculationInputs = objectMapper.convertValue(inputsObj, 
                    new TypeReference<List<InputRateInfo>>() {});
                dto.setCalculationInputs(calculationInputs);
            } catch (IllegalArgumentException e) {
                log.warn("Betikten 'calculationInputs' dönüştürülemedi, kural [{}]: {}. Girdiler: {}",
                        rule.getOutputSymbol(), e.getMessage(), inputsObj);
                dto.setCalculationInputs(new ArrayList<>()); // Set to empty list or handle as error
            }
        } else {
            dto.setCalculationInputs(new ArrayList<>());
        }
        
        if (resultMap.containsKey("calculatedByStrategy")) {
            dto.setCalculatedByStrategy((String) resultMap.get("calculatedByStrategy"));
        }
        
        return dto;
    }

    private BigDecimal convertToBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                log.warn("String, BigDecimal'e dönüştürülemedi: {}", value, e);
                return null;
            }
        }
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        // For other types, try converting to string first
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("BigDecimal'e dönüştürülemedi: {}", value, e);
            return null;
        }
    }

    private long convertToLong(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                log.warn("String, long'a dönüştürülemedi: {}. Varsayılan kullanılıyor: {}", value, defaultValue, e);
                return defaultValue;
            }
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Long'a dönüştürülemedi: {}. Varsayılan kullanılıyor: {}", value, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Adapts input rates map to ensure symbol format consistency for scripts
     * Using SymbolUtils to convert between formats
     */
    private Map<String, BaseRateDto> adaptInputRatesForScript(Map<String, BaseRateDto> originalInputRates) {
        Map<String, BaseRateDto> adaptedRates = new HashMap<>();
        
        if (originalInputRates == null || originalInputRates.isEmpty()) {
            log.warn("adaptInputRatesForScript: Orijinal inputRates boş veya null");
            return adaptedRates;
        }
        
        // First pass: add all original entries
        adaptedRates.putAll(originalInputRates);
        
        // Second pass: add entries with alternative symbols (add but don't replace)
        for (Map.Entry<String, BaseRateDto> entry : originalInputRates.entrySet()) {
            String originalKey = entry.getKey();
            BaseRateDto rate = entry.getValue();
            
            if (originalKey == null || originalKey.isEmpty() || rate == null) {
                log.warn("adaptInputRatesForScript: Geçersiz giriş, anahtar={}, rate={}", originalKey, rate);
                continue;
            }
            
            // Handle calc_rate: prefix
            if (originalKey.startsWith("calc_rate:")) {
                String unprefixedKey = originalKey.substring("calc_rate:".length());
                if (!adaptedRates.containsKey(unprefixedKey)) {
                    adaptedRates.put(unprefixedKey, rate);
                    log.debug("adaptInputRatesForScript: Öneksiz alternatif eklendi: {} -> {}", originalKey, unprefixedKey);
                }
            } else {
                // Add prefixed version if it doesn't exist
                String prefixedKey = "calc_rate:" + originalKey;
                if (!adaptedRates.containsKey(prefixedKey)) {
                    adaptedRates.put(prefixedKey, rate);
                    log.debug("adaptInputRatesForScript: Önekli alternatif eklendi: {} -> {}", originalKey, prefixedKey);
                }
            }
            
            // Add both slashed and unslashed versions for compatibility
            if (!originalKey.contains("/")) {
                // Add version with slashes for scripts that expect it
                String slashedSymbol = com.toyota.mainapp.util.SymbolUtils.formatWithSlash(originalKey);
                if (!originalKey.equals(slashedSymbol) && !adaptedRates.containsKey(slashedSymbol)) {
                    adaptedRates.put(slashedSymbol, rate);
                    log.debug("adaptInputRatesForScript: Alternatif eğik çizgili sembol eklendi: {} -> {}", originalKey, slashedSymbol);
                }
                
                // Also add prefixed version with slashes
                String prefixedSlashedSymbol = "calc_rate:" + slashedSymbol;
                if (!adaptedRates.containsKey(prefixedSlashedSymbol)) {
                    adaptedRates.put(prefixedSlashedSymbol, rate);
                    log.debug("adaptInputRatesForScript: Önekli eğik çizgili alternatif eklendi: {} -> {}", originalKey, prefixedSlashedSymbol);
                }
            } else {
                // Add version without slashes for scripts that expect it
                String unslashedSymbol = com.toyota.mainapp.util.SymbolUtils.removeSlash(originalKey);
                if (!originalKey.equals(unslashedSymbol) && !adaptedRates.containsKey(unslashedSymbol)) {
                    adaptedRates.put(unslashedSymbol, rate);
                    log.debug("adaptInputRatesForScript: Alternatif eğik çizgisiz sembol eklendi: {} -> {}", originalKey, unslashedSymbol);
                }
                
                // Also add prefixed version without slashes
                String prefixedUnslashedSymbol = "calc_rate:" + unslashedSymbol;
                if (!adaptedRates.containsKey(prefixedUnslashedSymbol)) {
                    adaptedRates.put(prefixedUnslashedSymbol, rate);
                    log.debug("adaptInputRatesForScript: Önekli eğik çizgisiz alternatif eklendi: {} -> {}", originalKey, prefixedUnslashedSymbol);
                }
            }
            
            // Also handle _AVG suffix variants
            if (originalKey.endsWith("_AVG")) {
                String baseSymbol = originalKey.substring(0, originalKey.length() - 4);
                // Add version with slashes for base symbol if needed
                if (!baseSymbol.contains("/")) {
                    String slashedBase = com.toyota.mainapp.util.SymbolUtils.formatWithSlash(baseSymbol);
                    String slashedKeyWithAvg = slashedBase + "_AVG";
                    if (!adaptedRates.containsKey(slashedKeyWithAvg)) {
                        adaptedRates.put(slashedKeyWithAvg, rate);
                        log.debug("adaptInputRatesForScript: _AVG için alternatif sembol eklendi: {} -> {}", originalKey, slashedKeyWithAvg);
                    }
                } else {
                    String unslashedBase = com.toyota.mainapp.util.SymbolUtils.removeSlash(baseSymbol);
                    String unslashedKeyWithAvg = unslashedBase + "_AVG";
                    if (!adaptedRates.containsKey(unslashedKeyWithAvg)) {
                        adaptedRates.put(unslashedKeyWithAvg, rate);
                        log.debug("adaptInputRatesForScript: _AVG için alternatif sembol eklendi: {} -> {}", originalKey, unslashedKeyWithAvg);
                    }
                }
            }
        }
        
        log.debug("adaptInputRatesForScript: {} orijinal sembole karşılık toplam {} sembol oluşturuldu", 
                originalInputRates.size(), adaptedRates.size());
        
        return adaptedRates;
    }
}
