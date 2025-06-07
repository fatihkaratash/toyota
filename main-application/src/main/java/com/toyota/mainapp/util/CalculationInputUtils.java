package com.toyota.mainapp.util;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ✅ UTILITY: Calculation input collection for pipeline stages
 * Standardized input gathering for average and cross rate calculations
 */
@Component
@Slf4j
public class CalculationInputUtils {

    /**
     * ✅ COLLECT INPUT RATES: For average calculations (raw rates only)
     */
    public Map<String, BaseRateDto> collectInputRates(
            CalculationRuleDto rule,
            ExecutionContext context,
            RateCacheService rateCacheService) {

        Map<String, BaseRateDto> inputRates = new HashMap<>();

        if (rule.getInputSymbols() == null || rule.getInputSymbols().isEmpty()) {
            log.warn("No input symbols defined for rule: {}", rule.getOutputSymbol());
            return inputRates;
        }

        // ✅ BATCH OPERATION: Get raw rates for the required symbol
        for (String inputSymbol : rule.getInputSymbols()) {
            // Get all providers for this symbol from cache
            List<String> providers = List.of("TCPProvider2", "RESTProvider1"); // Could be config-driven

            Map<String, BaseRateDto> symbolRates = rateCacheService.getRawRatesBatch(inputSymbol, providers);

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
     * ✅ COLLECT INPUT RATES FOR CROSS: Mix of raw and calculated rates
     */
    public Map<String, BaseRateDto> collectInputRatesForCross(
            CalculationRuleDto rule,
            ExecutionContext context,
            RateCacheService rateCacheService) {

        Map<String, BaseRateDto> inputRates = new HashMap<>();

        if (rule.getInputSymbols() == null || rule.getInputSymbols().isEmpty()) {
            log.warn("No input symbols defined for cross rule: {}", rule.getOutputSymbol());
            return inputRates;
        }

        for (String inputSymbol : rule.getInputSymbols()) {
            // ✅ STRATEGY: Try calculated rate first, then raw rate
            BaseRateDto rate = null;

            // Check if it's a calculated rate (ends with _AVG or _CROSS)
            if (inputSymbol.endsWith("_AVG") || inputSymbol.endsWith("_CROSS")) {
                rate = rateCacheService.getCalculatedRate(inputSymbol);
                log.debug("Looking for calculated rate: {}, found: {}", inputSymbol, rate != null);
            }

            // If not found as calculated, try as raw rate
            if (rate == null) {
                // Try to get from execution context first (recently calculated)
                rate = context.getCalculatedRates().stream()
                        .filter(r -> inputSymbol.equals(r.getSymbol()))
                        .findFirst()
                        .orElse(null);

                if (rate == null) {
                    // Get from cache as raw rate
                    List<String> providers = List.of("TCPProvider2", "RESTProvider1");
                    Map<String, BaseRateDto> symbolRates = rateCacheService.getRawRatesBatch(inputSymbol, providers);
                    if (!symbolRates.isEmpty()) {
                        rate = symbolRates.values().iterator().next();
                    }
                }
            }

            if (rate != null) {
                inputRates.put(inputSymbol, rate);
                log.debug("Collected input rate for cross calculation: {} = {} bid, {} ask",
                        inputSymbol, rate.getBid(), rate.getAsk());
            } else {
                log.warn("Could not find required input rate: {} for cross rule: {}",
                        inputSymbol, rule.getOutputSymbol());
            }
        }

        log.debug("Collected {} input rates for cross rule: {}", inputRates.size(), rule.getOutputSymbol());
        return inputRates;
    }
}
