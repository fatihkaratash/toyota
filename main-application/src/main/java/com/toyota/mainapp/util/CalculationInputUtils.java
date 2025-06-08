package com.toyota.mainapp.util;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ✅ COMPLETE: Input collection utilities for calculation stages
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalculationInputUtils {

    private final RateCacheService rateCacheService;

    /**
     * ✅ CLEAN: Collect RAW inputs using simplified cache interface
     */
    public Map<String, BaseRateDto> collectRawInputs(CalculationRuleDto rule) {
        Map<String, BaseRateDto> inputRates = new HashMap<>();

        if (rule.getInputSymbols() == null || rule.getInputSymbols().isEmpty()) {
            log.warn("No input symbols defined for rule: {}", rule.getOutputSymbol());
            return inputRates;
        }

        // ✅ BATCH OPERATION: Get raw rates for the required symbol
        for (String inputSymbol : rule.getInputSymbols()) {
            // Get all providers for this symbol from cache
            List<String> providers = List.of("TCPProvider2", "RESTProvider1"); // Could be config-driven

 Map<String, BaseRateDto> symbolRates = rateCacheService.getRawRatesForSymbol(inputSymbol, providers);

            // Add rates with provider-specific keys
            symbolRates.forEach((provider, rate) -> {
                String key = provider + "_" + inputSymbol;
                inputRates.put(key, rate);
                log.debug("Collected raw rate: {} from provider: {}", inputSymbol, provider);
            });

            // Also add with symbol key for compatibility
            if (!symbolRates.isEmpty()) {
                BaseRateDto firstRate = symbolRates.values().iterator().next();
                inputRates.put(inputSymbol, firstRate);
            }
        }

        log.debug("Collected {} input rates for rule: {}", inputRates.size(), rule.getOutputSymbol());
        return inputRates;
    }

    /**
     * ✅ CLEAN: Collect calculated inputs using simplified cache interface
     */
    public Map<String, BaseRateDto> collectCalculatedInputs(CalculationRuleDto rule) {
        Map<String, BaseRateDto> inputs = new HashMap<>();

        if (rule.getRequiredCalculatedRates() == null || rule.getRequiredCalculatedRates().isEmpty()) {
            return inputs;
        }

        // ✅ ENHANCED: Try multiple key patterns for calculated rates
        for (String symbol : rule.getRequiredCalculatedRates()) {
            BaseRateDto rate = null;
            
            // Try different key patterns to find the calculated rate
            String[] possibleSymbols = {
                symbol,                    // "CALC_EURUSD_AVG"
                symbol + "_AVG",          // "EURUSD" -> "EURUSD_AVG"
                "CALC_" + symbol,         // "EURUSD_AVG" -> "CALC_EURUSD_AVG"
                symbol.replace("CALC_", "")  // "CALC_EURUSD_AVG" -> "EURUSD_AVG"
            };
            
            for (String trySymbol : possibleSymbols) {
                rate = rateCacheService.getCalculatedRate(trySymbol);
                if (rate != null) {
                    inputs.put(symbol, rate);
                    log.debug("✅ Found calculated rate '{}' with key: {}", symbol, trySymbol);
                    break;
                }
            }
            
            if (rate == null) {
                log.debug("❌ Missing calculated rate: {}", symbol);
            }
        }
        
        log.debug("Collected {} calculated rates", inputs.size());
        return inputs;
    }

    /**
     * ✅ Collect ALL inputs (both raw and calculated) for comprehensive calculations
     */
    public Map<String, BaseRateDto> collectAllInputs(CalculationRuleDto rule) {
        Map<String, BaseRateDto> allInputs = new HashMap<>();

        // Collect raw inputs
        Map<String, BaseRateDto> rawInputs = collectRawInputs(rule);
        allInputs.putAll(rawInputs);

        // Collect calculated inputs
        Map<String, BaseRateDto> calculatedInputs = collectCalculatedInputs(rule);
        allInputs.putAll(calculatedInputs);

        log.debug("Collected total {} inputs for rule: {} (raw: {}, calculated: {})",
                allInputs.size(), rule.getOutputSymbol(), rawInputs.size(), calculatedInputs.size());

        return allInputs;
    }

    /**
     * ✅ Validate if all required inputs are available
     */
    public boolean hasAllRequiredInputs(CalculationRuleDto rule, Map<String, BaseRateDto> availableInputs) {
        // Check raw sources
        List<String> rawSources = rule.getRawSources();
        if (rawSources != null && !rawSources.isEmpty()) {
            for (String symbol : rawSources) {
                // For raw sources, we need at least one provider rate for the symbol
                boolean hasSymbol = availableInputs.values().stream()
                        .anyMatch(rate -> rate.getSymbol().equals(symbol));
                if (!hasSymbol) {
                    log.debug("Missing raw input for symbol: {}", symbol);
                    return false;
                }
            }
        }

        // Check calculated dependencies
        List<String> calculatedSources = rule.getRequiredCalculatedRates();
        if (calculatedSources != null && !calculatedSources.isEmpty()) {
            for (String symbol : calculatedSources) {
                if (!availableInputs.containsKey(symbol)) {
                    log.debug("Missing calculated input for symbol: {}", symbol);
                    return false;
                }
            }
        }

        return true;
    }
}
