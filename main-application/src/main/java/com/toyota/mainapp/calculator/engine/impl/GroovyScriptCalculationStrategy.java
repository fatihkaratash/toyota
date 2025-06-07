package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component("groovyScriptCalculationStrategy")
@Slf4j
@RequiredArgsConstructor
public class GroovyScriptCalculationStrategy implements CalculationStrategy {

    private final ResourceLoader resourceLoader;

    // ✅ ENHANCED: Script cache with better performance
    private final Map<String, String> scriptCache = new ConcurrentHashMap<>();
    private static final int MAX_SCRIPT_CACHE_SIZE = 50;

    @Override
    public Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        try {
            // ✅ FIXED: Get script path from rule implementation field
            String scriptPath = rule.getImplementation();
            if (scriptPath == null || scriptPath.trim().isEmpty()) {
                log.error("No script implementation specified for rule: {}", rule.getOutputSymbol());
                return Optional.empty();
            }

            String scriptContent = loadScript(scriptPath);

            // ✅ ENHANCED: Create optimized binding with rule context
            Binding binding = createScriptBinding(rule, inputRates);

            // ✅ PERFORMANCE: Reuse compiler configuration
            CompilerConfiguration compilerConfig = new CompilerConfiguration();
            compilerConfig.setScriptBaseClass("groovy.lang.Script");
            
            GroovyShell shell = new GroovyShell(getClass().getClassLoader(), binding, compilerConfig);
            Object result = shell.evaluate(scriptContent);

            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                BaseRateDto dto = mapToBaseRateDto(resultMap, rule);
                
                log.info("✅ Script execution successful: {} -> bid={}, ask={}", 
                        rule.getOutputSymbol(), dto.getBid(), dto.getAsk());
                return Optional.of(dto);
            } else {
                log.error("❌ Script returned invalid result type for rule: {} (expected Map, got {})", 
                        rule.getOutputSymbol(), result != null ? result.getClass().getSimpleName() : "null");
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("❌ Script execution error for rule {}: {}", rule.getOutputSymbol(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * ✅ FIXED: Enhanced script binding with proper parameter handling
     */
    private Binding createScriptBinding(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        Binding binding = new Binding();
        
        // Core script variables
        binding.setVariable("log", log);
        binding.setVariable("outputSymbol", rule.getOutputSymbol());
        
        // ✅ FIXED: Handle Map<String, Object> parameters correctly
        if (rule.getInputParameters() != null && !rule.getInputParameters().isEmpty()) {
            for (Map.Entry<String, Object> param : rule.getInputParameters().entrySet()) {
                String value = param.getValue() != null ? param.getValue().toString() : "";
                binding.setVariable(param.getKey(), value);
                log.debug("Script parameter: {} = {}", param.getKey(), value);
            }
        }

        // ✅ OPTIMIZED: Adapt input rates for script consumption
        Map<String, BaseRateDto> adaptedInputs = adaptInputRatesForScript(inputRates, rule);
        binding.setVariable("inputRates", adaptedInputs);
        
        log.debug("Script binding created: {} parameters, {} input rates", 
                rule.getInputParameters() != null ? rule.getInputParameters().size() : 0, 
                adaptedInputs.size());
        
        return binding;
    }

    /**
     * ✅ ENHANCED: Smart input adaptation based on rule requirements
     */
    private Map<String, BaseRateDto> adaptInputRatesForScript(Map<String, BaseRateDto> inputRates, CalculationRuleDto rule) {
        Map<String, BaseRateDto> adaptedRates = new HashMap<>();

        for (Map.Entry<String, BaseRateDto> entry : inputRates.entrySet()) {
            BaseRateDto rate = entry.getValue();
            String symbol = rate.getSymbol();
            String normalizedSymbol = SymbolUtils.normalizeSymbol(symbol);

            if (SymbolUtils.isValidSymbol(normalizedSymbol)) {
                // ✅ CORE: Add multiple key formats for maximum script compatibility
                adaptedRates.put(normalizedSymbol, rate);
                adaptedRates.put(normalizedSymbol + "_AVG", rate);
                adaptedRates.put(SymbolUtils.addSlash(normalizedSymbol), rate);
                adaptedRates.put(SymbolUtils.addSlash(normalizedSymbol) + "_AVG", rate);
                
                // ✅ RULE-SPECIFIC: Add keys that match inputSymbols from config
                if (rule.getInputSymbols() != null) {
                    for (String inputSymbol : rule.getInputSymbols()) {
                        if (SymbolUtils.symbolsEquivalent(inputSymbol, normalizedSymbol)) {
                            adaptedRates.put(inputSymbol, rate);
                        }
                    }
                }
                
                log.debug("Adapted rate '{}' with keys: {}, {}, {}", 
                        symbol, normalizedSymbol, normalizedSymbol + "_AVG", SymbolUtils.addSlash(normalizedSymbol));
            }
        }

        log.info("Adapted {} input rates to {} key variants for script: {}", 
                inputRates.size(), adaptedRates.size(), rule.getOutputSymbol());

        return adaptedRates;
    }

    private String loadScript(String scriptPath) throws IOException {
        // ✅ PERFORMANCE: Check cache first
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

            // ✅ PERFORMANCE: Cache with size limit
            if (scriptCache.size() < MAX_SCRIPT_CACHE_SIZE) {
                scriptCache.put(scriptPath, scriptContent);
                log.debug("Script cached: {}", scriptPath);
            }

            return scriptContent;
        } catch (IOException e) {
            log.error("Failed to load script: {}", scriptPath, e);
            throw e;
        }
    }

    private BaseRateDto mapToBaseRateDto(Map<String, Object> resultMap, CalculationRuleDto rule) {
        BaseRateDto result = new BaseRateDto();

        // ✅ ARCHITECTURE: Maintain symbol normalization
        String normalizedOutputSymbol = SymbolUtils.normalizeSymbol(rule.getOutputSymbol());
        result.setSymbol(normalizedOutputSymbol);
        result.setRateType(com.toyota.mainapp.dto.model.RateType.CALCULATED);

        // ✅ ENHANCED: Better calculation type determination
        String calculationType = SymbolUtils.determineCalculationType(rule.getOutputSymbol(), rule.getStrategyType());
        
        // Set calculation type if field exists (defensive programming)
        try {
            java.lang.reflect.Field calcTypeField = BaseRateDto.class.getDeclaredField("calculationType");
            calcTypeField.setAccessible(true);
            calcTypeField.set(result, calculationType);
            log.debug("CalculationType set: {}", calculationType);
        } catch (NoSuchFieldException e) {
            log.debug("BaseRateDto has no calculationType field - skipping");
        } catch (Exception e) {
            log.warn("Could not set calculationType: {}", e.getMessage());
        }

        // ✅ CORE: Process bid/ask with validation
        Object bidObj = resultMap.get("bid");
        Object askObj = resultMap.get("ask");

        if (bidObj instanceof Number && askObj instanceof Number) {
            result.setBid(new BigDecimal(bidObj.toString()));
            result.setAsk(new BigDecimal(askObj.toString()));
        } else {
            throw new IllegalArgumentException("Invalid bid/ask types in script result for " + rule.getOutputSymbol());
        }

        // ✅ CORE: Handle timestamp
        Object timestampObj = resultMap.get("rateTimestamp");
        if (timestampObj == null) {
            timestampObj = resultMap.get("timestamp");
        }
        
        if (timestampObj instanceof Number) {
            result.setTimestamp(((Number) timestampObj).longValue());
        } else {
            result.setTimestamp(System.currentTimeMillis());
        }

        // ✅ ARCHITECTURE: Consistent provider naming
        result.setProviderName("CALCULATED");

        log.debug("✅ Script result mapped: symbol={}, type={}, bid={}, ask={}", 
                result.getSymbol(), calculationType, result.getBid(), result.getAsk());
        
        return result;
    }

    @Override
    public String getStrategyName() {
        return "groovyScriptCalculationStrategy";
    }

    @Override
    public String getStrategyType() {
        return "CROSS";
    }

    @Override
    public boolean canHandle(CalculationRuleDto rule) {
        return rule != null && 
               "CROSS".equalsIgnoreCase(rule.getType()) &&
               "groovyScriptCalculationStrategy".equals(rule.getStrategyType()) &&
               rule.getImplementation() != null &&
               !rule.getImplementation().trim().isEmpty();
    }
    
    /**
     * ✅ NEW: Clear script cache (for testing/reloading)
     */
    public void clearScriptCache() {
        scriptCache.clear();
        log.info("🔄 Script cache cleared");
    }
    
    /**
     * ✅ NEW: Get script cache statistics
     */
    public Map<String, Integer> getScriptCacheStats() {
        return Map.of("scriptCacheSize", scriptCache.size());
    }
}