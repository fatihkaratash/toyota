package com.toyota.mainapp.calculator.engine.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import com.toyota.mainapp.dto.RawRateDto;
import com.toyota.mainapp.dto.common.InputRateInfo;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public Optional<CalculatedRateDto> calculate(CalculationRuleDto rule, Map<String, RawRateDto> inputRates) {
        String scriptPath = rule.getImplementation(); // e.g., "scripts/eur_try_calculator.groovy"
        log.debug("Groovy betiği çalıştırılıyor, kural [{}]: {}", rule.getOutputSymbol(), scriptPath);

        try {
            Resource scriptResource = resourceLoader.getResource("classpath:" + scriptPath);
            if (!scriptResource.exists()) {
                log.error("Groovy betiği bulunamadı, dizin: {}", scriptPath);
                return Optional.empty();
            }

            String scriptContent = new String(scriptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            CompilerConfiguration compilerConfig = new CompilerConfiguration();

            Binding binding = new Binding();
            binding.setVariable("cache", this.rateCacheService); // Injected cache service
            binding.setVariable("log", log); // This class's logger is passed to the script
            binding.setVariable("outputSymbol", rule.getOutputSymbol());
            binding.setVariable("inputRates", inputRates); // Pass the input rates directly to the script
            
            if (rule.getInputParameters() != null) {
                rule.getInputParameters().forEach(binding::setVariable);
            }

            GroovyShell shell = new GroovyShell(getClass().getClassLoader(), binding, compilerConfig);
            Object result = shell.evaluate(scriptContent);

            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                
                CalculatedRateDto dto = mapToCalculatedRateDto(resultMap, rule);
                dto.setCalculatedByStrategy(scriptPath); // Assign script path
                log.info("Groovy betiği [{}] başarıyla çalıştırıldı, kural [{}]: {}", scriptPath, rule.getOutputSymbol(), dto);
                return Optional.of(dto);
            } else {
                log.error("Groovy betiği [{}], kural [{}] için Map dönmedi. Dönen değer: {}", scriptPath, rule.getOutputSymbol(), result);
                return Optional.empty();
            }

        } catch (IOException e) {
            log.error("Groovy betiği [{}] okunamadı: {}", scriptPath, e.getMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Groovy betiği [{}] çalıştırılırken hata oluştu, kural [{}]: {}", scriptPath, rule.getOutputSymbol(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public String getStrategyName() {
        return "groovyScriptCalculationStrategy";
    }
    
    private CalculatedRateDto mapToCalculatedRateDto(Map<String, Object> resultMap, CalculationRuleDto rule) {
        CalculatedRateDto dto = new CalculatedRateDto();
        dto.setSymbol(rule.getOutputSymbol()); // Default to rule's output symbol, can be overridden by script
        
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
                List<InputRateInfo> calculationInputs = objectMapper.convertValue(inputsObj, new TypeReference<List<InputRateInfo>>() {});
                dto.setCalculationInputs(calculationInputs);
            } catch (IllegalArgumentException e) {
                log.warn("Betikten 'calculationInputs' dönüştürülemedi, kural [{}]: {}. Girdiler: {}",
                        rule.getOutputSymbol(), e.getMessage(), inputsObj);
                dto.setCalculationInputs(new ArrayList<>()); // Set to empty list or handle as error
            }
        } else {
            dto.setCalculationInputs(new ArrayList<>());
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
}
