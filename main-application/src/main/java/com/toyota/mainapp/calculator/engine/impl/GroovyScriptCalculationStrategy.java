package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component("groovyScriptCalculationStrategy")
@Primary // ✅ PRIMARY STRATEGY
@Slf4j
@RequiredArgsConstructor
public class GroovyScriptCalculationStrategy implements CalculationStrategy {

    private final ResourceLoader resourceLoader;

    // Script cache - performance için
    private final Map<String, String> scriptCache = new ConcurrentHashMap<>();

    @Override
    public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        try {
            String scriptContent = loadScript(rule.getImplementation());

            Binding binding = new Binding();
            binding.setVariable("log", log);
            binding.setVariable("outputSymbol", rule.getOutputSymbol());

            // Input parameters
            if (rule.getInputParameters() != null && !rule.getInputParameters().isEmpty()) {
                for (Map.Entry<String, String> param : rule.getInputParameters().entrySet()) {
                    binding.setVariable(param.getKey(), param.getValue());
                }
            }

            // ✅ ADAPTATION LOGIC - GERİ EKLENDİ
            Map<String, BaseRateDto> adaptedInputs = adaptInputRatesForScript(inputRates);
            binding.setVariable("inputRates", adaptedInputs);

            log.debug("Script execution for rule: {}, adapted inputs: {}",
                    rule.getOutputSymbol(), adaptedInputs.keySet());

            // Script execution...
            CompilerConfiguration compilerConfig = new CompilerConfiguration();
            GroovyShell shell = new GroovyShell(getClass().getClassLoader(), binding, compilerConfig);
            Object result = shell.evaluate(scriptContent);

            // Expecting the script to return a Map<String, Object> with bid, ask,
            // timestamp, etc.
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                BaseRateDto dto = mapToBaseRateDto(resultMap, rule);
                return Optional.of(dto);
            } else {
                log.error("Script did not return a Map result for rule: {}", rule.getOutputSymbol());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Script execution error for rule {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * ✅ GERİ EKLENDİ - Input rates'i script'lerin beklediği formatlara adapt et
     */
    private Map<String, BaseRateDto> adaptInputRatesForScript(Map<String, BaseRateDto> inputRates) {
        Map<String, BaseRateDto> adaptedRates = new HashMap<>();

        for (Map.Entry<String, BaseRateDto> entry : inputRates.entrySet()) {
            BaseRateDto rate = entry.getValue();
            String symbol = rate.getSymbol();
            String normalizedSymbol = SymbolUtils.normalizeSymbol(symbol);

            if (SymbolUtils.isValidSymbol(normalizedSymbol)) {
                // 1. Normalized format (USDTRY)
                adaptedRates.put(normalizedSymbol, rate);

                // 2. AVG suffix (USDTRY_AVG)
                adaptedRates.put(normalizedSymbol + "_AVG", rate);

                // 3. Slash format (USD/TRY)
                String slashFormat = SymbolUtils.addSlash(normalizedSymbol);
                adaptedRates.put(slashFormat, rate);

                // 4. Slash + AVG format (USD/TRY_AVG)
                adaptedRates.put(slashFormat + "_AVG", rate);

                // 5. CROSS suffix (USDTRY_CROSS)
                adaptedRates.put(normalizedSymbol + "_CROSS", rate);

                // 6. CALC suffix (USDTRY_CALC)
                adaptedRates.put(normalizedSymbol + "_CALC", rate);

                log.debug("Adapted rate '{}' to multiple formats: {}, {}, {}, {}",
                        symbol, normalizedSymbol, normalizedSymbol + "_AVG", slashFormat, slashFormat + "_AVG");
            } else {
                log.warn("Skipping invalid symbol for adaptation: '{}'", symbol);
            }
        }

        log.info("Adapted {} input rates to {} key formats for script execution",
                inputRates.size(), adaptedRates.size());

        return adaptedRates;
    }

    private String loadScript(String scriptPath) throws IOException {
        // Cache kontrolü
        String cachedScript = scriptCache.get(scriptPath);
        if (cachedScript != null) {
            return cachedScript;
        }

        try {
            var resource = resourceLoader.getResource("classpath:" + scriptPath);
            if (!resource.exists()) {
                throw new IOException("Script not found: " + scriptPath);
            }

            String scriptContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Cache'e ekle
            scriptCache.put(scriptPath, scriptContent);
            log.debug("Script loaded and cached: {}", scriptPath);

            return scriptContent;
        } catch (IOException e) {
            log.error("Failed to load script: {}", scriptPath, e);
            throw e;
        }
    }

    private BaseRateDto mapToBaseRateDto(Map<String, Object> resultMap, CalculationRuleDto rule) {
        BaseRateDto result = new BaseRateDto();

        // Output symbol'ü normalize et
        String normalizedOutputSymbol = SymbolUtils.normalizeSymbol(rule.getOutputSymbol());
        result.setSymbol(normalizedOutputSymbol);

        // Rate type set et
        result.setRateType(com.toyota.mainapp.dto.model.RateType.CALCULATED);

        // Calculation type'ı belirle - Symbol'den çıkar
        String calculationType;
        if (rule.getOutputSymbol().contains("CROSS")) {
            calculationType = "CROSS";
        } else if (rule.getOutputSymbol().contains("AVG")) {
            calculationType = "AVG";
        } else {
            calculationType = "AVG"; // Default
        }

        // BaseRateDto'da calculationType field varsa set et
        try {
            // Reflection ile field varlığını kontrol et
            java.lang.reflect.Field calcTypeField = BaseRateDto.class.getDeclaredField("calculationType");
            calcTypeField.setAccessible(true);
            calcTypeField.set(result, calculationType);
            log.debug("CalculationType set: {}", calculationType);
        } catch (NoSuchFieldException e) {
            log.debug("BaseRateDto'da calculationType field yok, atlanıyor");
            // Field yoksa bir şey yapma, problem değil
        } catch (Exception e) {
            log.warn("CalculationType set edilemedi: {}", e.getMessage());
        }

        // Bid/Ask değerleri
        Object bidObj = resultMap.get("bid");
        Object askObj = resultMap.get("ask");

        if (bidObj instanceof Number && askObj instanceof Number) {
            result.setBid(new BigDecimal(bidObj.toString()));
            result.setAsk(new BigDecimal(askObj.toString()));
        } else {
            throw new IllegalArgumentException("Invalid bid/ask types in script result");
        }

        // Timestamp
        Object timestampObj = resultMap.get("timestamp");
        if (timestampObj instanceof Number) {
            result.setTimestamp(((Number) timestampObj).longValue());
        } else {
            result.setTimestamp(System.currentTimeMillis());
        }

        // Provider bilgisi - calculated için
        result.setProviderName("CALCULATED");

        log.debug("Mapped script result to BaseRateDto: symbol={}, calculationType={}, bid={}, ask={}",
                result.getSymbol(), calculationType, result.getBid(), result.getAsk());
        return result;
    }

    @Override
    public String getStrategyName() {
        return "groovyScriptCalculationStrategy";
    }

    // SİLİNDİ: adaptInputRatesForScript() method (200+ satır kaldırıldı)
    // SİLİNDİ: Format conversion logic
    // SİLİNDİ: calc_rate: prefix handling
    // SİLİNDİ: Slash format handling
    // SİLİNDİ: _AVG suffix handling
    // SİLİNDİ: Complex key mapping logic
}