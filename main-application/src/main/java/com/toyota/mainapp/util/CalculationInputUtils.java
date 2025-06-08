package com.toyota.mainapp.util;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 Input collection utilities for calculation stages
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalculationInputUtils {

    private final RateCacheService rateCacheService;

    @Value("${app.calculation.provider-names:TCPProvider1,TCPProvider2,RESTProvider1}")
    private String providerNamesConfig;

    public Map<String, BaseRateDto> collectRawInputs(CalculationRuleDto rule) {
        Map<String, BaseRateDto> inputRates = new HashMap<>();

        if (rule.getInputSymbols() == null || rule.getInputSymbols().isEmpty()) {
            // Fallback to rawSources if inputSymbols is empty
            if (rule.getRawSources() != null && !rule.getRawSources().isEmpty()) {
                return collectRawInputsFromSources(rule.getRawSources());
            }
            log.warn("No input symbols or raw sources defined for rule: {}", rule.getOutputSymbol());
            return inputRates;
        }

        return collectRawInputsFromSources(rule.getInputSymbols());
    }

    private Map<String, BaseRateDto> collectRawInputsFromSources(List<String> sources) {
        Map<String, BaseRateDto> inputRates = new HashMap<>();
        
        List<String> providerNames = getProviderNames();

        for (String inputSymbol : sources) {
            String normalizedSymbol = SymbolUtils.normalizeSymbol(inputSymbol);
            
            // Get all providers for this symbol from cache
            Map<String, BaseRateDto> symbolRates = rateCacheService.getRawRatesForSymbol(normalizedSymbol, providerNames);

            symbolRates.forEach((provider, rate) -> {
                if (rate != null && RateCalculationUtils.isValidRate(rate)) {
                    String providerKey = provider + "_" + normalizedSymbol;
                    inputRates.put(providerKey, rate);
                    log.debug("✅ Collected valid raw rate: {} from provider: {}", normalizedSymbol, provider);
                }
            });

            if (!symbolRates.isEmpty()) {
                BaseRateDto bestRate = selectBestRate(symbolRates.values());
                if (bestRate != null) {
                    inputRates.put(normalizedSymbol, bestRate);
                    inputRates.put(inputSymbol, bestRate); // Original symbol format too
                }
            }
        }

        log.info("📊 Collected {} raw input rates from {} symbols", inputRates.size(), sources.size());
        return inputRates;
    }

    private BaseRateDto selectBestRate(java.util.Collection<BaseRateDto> rates) {
        return rates.stream()
                .filter(RateCalculationUtils::isValidRate)
                .max((r1, r2) -> Long.compare(
                    r1.getTimestamp() != null ? r1.getTimestamp() : 0L,
                    r2.getTimestamp() != null ? r2.getTimestamp() : 0L
                ))
                .orElse(null);
    }

    public Map<String, BaseRateDto> collectCalculatedInputs(CalculationRuleDto rule) {
        Map<String, BaseRateDto> inputs = new HashMap<>();

        if (rule.getRequiredCalculatedRates() == null || rule.getRequiredCalculatedRates().isEmpty()) {
            return inputs;
        }

        for (String symbol : rule.getRequiredCalculatedRates()) {
            BaseRateDto rate = findCalculatedRate(symbol);
            if (rate != null) {
                inputs.put(symbol, rate);
                // Also add with normalized key
                String normalizedKey = SymbolUtils.normalizeSymbol(symbol);
                if (!symbol.equals(normalizedKey)) {
                    inputs.put(normalizedKey, rate);
                }
                log.debug("✅ Found calculated rate: {}", symbol);
            } else {
                log.debug("❌ Missing calculated rate: {}", symbol);
            }
        }
        
        log.info("📊 Collected {} calculated rates", inputs.size());
        return inputs;
    }

    private BaseRateDto findCalculatedRate(String symbol) {
        // Try multiple key patterns to find the calculated rate
        String[] possibleKeys = {
            symbol,                             
            SymbolUtils.normalizeSymbol(symbol), 
            symbol + "_AVG",                  
            symbol + "_CROSS",                 
            "CALC_" + symbol,                   
            symbol.replace("CALC_", ""),         
            symbol.replace("_AVG", ""),          
            symbol.replace("_CROSS", "")         
        };
        
        for (String tryKey : possibleKeys) {
            BaseRateDto rate = rateCacheService.getCalculatedRate(tryKey);
            if (rate != null && RateCalculationUtils.isValidRate(rate)) {
                log.debug("✅ Found calculated rate '{}' with key: {}", symbol, tryKey);
                return rate;
            }
        }
        
        return null;
    }

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

    private List<String> getProviderNames() {
        List<String> providers = new ArrayList<>();
        if (providerNamesConfig != null && !providerNamesConfig.trim().isEmpty()) {
            String[] parts = providerNamesConfig.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    providers.add(trimmed);
                }
            }
        }
        return providers;
    }
}
