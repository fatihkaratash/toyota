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
     * ✅ ENHANCED: Comprehensive input adaptation for cross-rate scripts
     */
    private Map<String, BaseRateDto> adaptInputRatesForScript(Map<String, BaseRateDto> inputRates, CalculationRuleDto rule) {
        Map<String, BaseRateDto> adaptedRates = new HashMap<>();

        log.info("🔄 Adapting {} input rates for script: {}", inputRates.size(), rule.getOutputSymbol());

        for (Map.Entry<String, BaseRateDto> entry : inputRates.entrySet()) {
            BaseRateDto rate = entry.getValue();
            String originalKey = entry.getKey();
            String symbol = rate.getSymbol();
            String normalizedSymbol = SymbolUtils.normalizeSymbol(symbol);

            log.debug("Processing rate: originalKey={}, symbol={}, normalized={}", 
                    originalKey, symbol, normalizedSymbol);

            if (SymbolUtils.isValidSymbol(normalizedSymbol)) {
                // ✅ COMPREHENSIVE: Add all possible key formats that scripts might expect
                
                // Basic formats
                adaptedRates.put(normalizedSymbol, rate);                    // "USDTRY"
                adaptedRates.put(normalizedSymbol + "_AVG", rate);           // "USDTRY_AVG"
                adaptedRates.put(SymbolUtils.addSlash(normalizedSymbol), rate); // "USD/TRY"
                adaptedRates.put(SymbolUtils.addSlash(normalizedSymbol) + "_AVG", rate); // "USD/TRY_AVG"
                
                // ✅ CRITICAL: Add the original input key (this is what cross-rate scripts expect)
                adaptedRates.put(originalKey, rate);                         // "USDTRY_AVG" from config
                
                // ✅ CONFIG-SPECIFIC: Match inputParameters from rule configuration
                if (rule.getInputParameters() != null) {
                    for (Map.Entry<String, Object> param : rule.getInputParameters().entrySet()) {
                        String paramKey = param.getKey();
                        String paramValue = param.getValue() != null ? param.getValue().toString() : "";
                        
                        // If parameter value matches this symbol, add it with parameter key
                        if (paramValue.equals(originalKey) || paramValue.equals(normalizedSymbol) || 
                            paramValue.equals(normalizedSymbol + "_AVG")) {
                            adaptedRates.put(paramValue, rate);
                            log.debug("✅ Added parameter-based key: {} -> {}", paramValue, symbol);
                        }
                    }
                }
                
                // ✅ RULE-SPECIFIC: Add keys that match requiredCalculatedRates from config
                if (rule.getRequiredCalculatedRates() != null) {
                    for (String requiredRate : rule.getRequiredCalculatedRates()) {
                        if (SymbolUtils.symbolsEquivalent(requiredRate, normalizedSymbol) ||
                            requiredRate.equals(originalKey)) {
                            adaptedRates.put(requiredRate, rate);
                            log.debug("✅ Added required-rate key: {} -> {}", requiredRate, symbol);
                        }
                    }
                }
                
                log.debug("✅ Adapted rate '{}' with multiple key formats", symbol);
            } else {
                log.warn("❌ Invalid symbol format, skipping: {}", symbol);
            }
        }

        log.info("✅ Input adaptation complete: {} rates adapted to {} key variants for script: {}", 
                inputRates.size(), adaptedRates.size(), rule.getOutputSymbol());
        
        // ✅ DEBUG: Log all adapted keys for troubleshooting
        log.debug("Adapted keys for script {}: {}", rule.getOutputSymbol(), adaptedRates.keySet());

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
        String calculationType = rule.getStrategyType(); // Use rule's strategyType directly
        
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

        // ✅ ENHANCED: Handle timestamp with multiple field names
        Object timestampObj = resultMap.get("rateTimestamp");
        if (timestampObj == null) {
            timestampObj = resultMap.get("timestamp");
        }
        
        if (timestampObj instanceof Number) {
            result.setTimestamp(((Number) timestampObj).longValue());
        } else {
            result.setTimestamp(System.currentTimeMillis());
            log.debug("Using current timestamp for {}", rule.getOutputSymbol());
        }

        // ✅ ARCHITECTURE: Consistent provider naming
        result.setProviderName("CALCULATED");

        log.debug("✅ Script result mapped: symbol={}, bid={}, ask={}, timestamp={}", 
                result.getSymbol(), result.getBid(), result.getAsk(), result.getTimestamp());
        
        return result;
    }

    @Override
    public String getStrategyName() {
        return "groovyScriptCalculationStrategy"; // ✅ Match config strategyType exactly
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