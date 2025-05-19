package com.toyota.mainapp.calculator.impl;

import com.toyota.mainapp.cache.RateCache;
import com.toyota.mainapp.calculator.CalculationStrategy;
import com.toyota.mainapp.calculator.RateCalculator;
import com.toyota.mainapp.calculator.config.CalculationConfig;
import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of the RateCalculator interface.
 * Manages calculation strategies and computes derived rates.
 */
@Service
public class DefaultRateCalculator implements RateCalculator {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultRateCalculator.class);
    
    private final RateCache rateCache;
    private final CalculationConfig calculationConfig;
    
    // Map of derived symbol to calculation strategy
    private final Map<String, CalculationStrategy> calculationStrategies = new ConcurrentHashMap<>();
    
    // Map of trigger symbol to set of derived symbols
    private final Map<String, Set<String>> triggerMap = new ConcurrentHashMap<>();
    
    @Autowired
    public DefaultRateCalculator(RateCache rateCache, CalculationConfig calculationConfig) {
        this.rateCache = rateCache;
        this.calculationConfig = calculationConfig;
    }
    
    @PostConstruct
    public void init() {
        // Load calculation strategies from configuration
        registerCalculationStrategies();
        
        // Build trigger map
        buildTriggerMap();
        
        logger.info("DefaultRateCalculator {} hesaplama stratejisi ile başlatıldı", 
                   calculationStrategies.size());
    }
    
    @Override
    public boolean shouldCalculate(String symbol) {
        return triggerMap.containsKey(symbol);
    }
    
    @Override
    public Map<String, CalculatedRate> calculateDerivedRates(Rate triggerRate) {
        if (triggerRate == null) {
            logger.warn("Tetikleyici kur null, türetilmiş kurlar hesaplanamıyor");
            return Collections.emptyMap();
        }
        
        String symbol = triggerRate.getSymbol();
        Set<String> derivedSymbols = triggerMap.get(symbol);
        
        if (derivedSymbols == null || derivedSymbols.isEmpty()) {
            logger.debug("{} sembolüne bağlı türetilmiş kur yok", symbol);
            return Collections.emptyMap();
        }
        
        Map<String, CalculatedRate> results = new HashMap<>();
        
        for (String derivedSymbol : derivedSymbols) {
            try {
                CalculationStrategy strategy = calculationStrategies.get(derivedSymbol);
                if (strategy == null) {
                    logger.warn("{} türetilmiş sembolü için hesaplama stratejisi bulunamadı", derivedSymbol);
                    continue;
                }
                
                // Get required source rates
                Map<String, Rate> sourceRates = getSourceRates(strategy);
                
                // Skip calculation if any required source rate is missing
                // Check if all required symbols are present in the fetched sourceRates
                boolean allSourcesPresent = true;
                for (String requiredSrcSymbol : strategy.getRequiredSourceSymbols()) {
                    // The sourceRates map keys might be platform-specific (e.g. PF1_USDTRY)
                    // while requiredSrcSymbol might be generic (e.g. USDTRY).
                    // The current getSourceRates fetches all rates for a symbol prefix.
                    // We need to ensure that for each required symbol, at least one provider's rate is present.
                    // This check needs refinement based on how sourceRates are keyed and how required symbols are defined.
                    // For now, let's assume strategy.getRequiredSourceSymbols() are specific enough (e.g. PF1_USDTRY)
                    // or that getSourceRates correctly aggregates them.
                    // A simpler check for now:
                    if (strategy.getRequiredSourceSymbols().stream().noneMatch(sourceRates::containsKey)) {
                         // This check is not entirely correct if required symbols are like "USDTRY" and sourceRates has "PF1_USDTRY"
                         // For now, we rely on the size check below, but this area might need more robust logic.
                    }
                }
                // A more direct check: are all symbols required by the strategy present in the sourceRates map?
                // This depends on whether strategy.getRequiredSourceSymbols() returns specific (PF1_USDTRY) or generic (USDTRY) symbols.
                // Assuming getSourceRates returns a map keyed by specific symbols (e.g. PF1_USDTRY)
                // and strategy.getRequiredSourceSymbols() lists these specific symbols.
                long foundCount = strategy.getRequiredSourceSymbols().stream().filter(sourceRates::containsKey).count();
                if (foundCount < strategy.getRequiredSourceSymbols().size()) {
                    logger.warn("{} türetilmiş sembolü için bazı kaynak kurlar eksik. Gerekli: {}, Bulunan: {}",
                                derivedSymbol, strategy.getRequiredSourceSymbols(), sourceRates.keySet());
                    continue;
                }
                
                // Calculate derived rate
                CalculatedRate calculatedRate = strategy.calculate(derivedSymbol, sourceRates);
                if (calculatedRate != null) {
                    results.put(derivedSymbol, calculatedRate);
                    logger.debug("Türetilmiş kur hesaplandı: {}", derivedSymbol);
                }
            } catch (Exception e) {
                logger.error("{} için türetilmiş kur hesaplanırken hata: {}", 
                            derivedSymbol, e.getMessage(), e);
            }
        }
        
        return results;
    }
    
    @Override
    public Map<String, Set<String>> getTriggerMap() {
        return Collections.unmodifiableMap(triggerMap);
    }
    
    @Override
    public Map<String, CalculationStrategy> getCalculationStrategies() {
        return Collections.unmodifiableMap(calculationStrategies);
    }
    
    private void registerCalculationStrategies() {
        // Register strategies from configuration
        calculationConfig.getCalculationStrategies().forEach(this::registerCalculationStrategy);
    }
    
    private void registerCalculationStrategy(CalculationStrategy strategy) {
        if (strategy == null) {
            logger.warn("Null hesaplama stratejisi kaydedilmeye çalışıldı");
            return;
        }
        
        String derivedSymbol = strategy.getStrategyId(); // This should be the target symbol like "EURTRY"
        calculationStrategies.put(derivedSymbol, strategy);
        logger.info("Türetilmiş sembol için hesaplama stratejisi kaydedildi: {}", derivedSymbol);
    }
    
    private void buildTriggerMap() {
        triggerMap.clear();
        
        // For each calculation strategy, add its source symbols to the trigger map
        for (Map.Entry<String, CalculationStrategy> entry : calculationStrategies.entrySet()) {
            String derivedSymbol = entry.getKey();
            CalculationStrategy strategy = entry.getValue();
            
            for (String sourceSymbol : strategy.getRequiredSourceSymbols()) {
                // sourceSymbol here is expected to be specific, e.g., "PF1_USDTRY"
                triggerMap.computeIfAbsent(sourceSymbol, k -> new HashSet<>())
                          .add(derivedSymbol);
                
                logger.debug("Tetikleyici eklendi: {} -> {}", sourceSymbol, derivedSymbol);
            }
        }
        
        logger.info("Tetikleyici haritası {} kaynak sembolü ile oluşturuldu", triggerMap.size());
    }
    
    private Map<String, Rate> getSourceRates(CalculationStrategy strategy) {
        Map<String, Rate> sourceRates = new HashMap<>();
        
        for (String sourceSymbolOrPrefix : strategy.getRequiredSourceSymbols()) {
            // Assuming getRequiredSourceSymbols() returns specific symbols like "PF1_USDTRY"
            // If it returns prefixes like "USDTRY", then getAllRawRates(sourceSymbolOrPrefix) is correct.
            // If it returns specific symbols, we might need getRawRate(symbol, platform) or adjust.
            // For now, let's assume required symbols are specific enough to be keys or prefixes.
            // The current EurTryFormula and GbpTryFormula use specific symbols like "PF1_USDTRY".
            // So, getAllRawRates should ideally fetch based on these specific names if they are unique,
            // or we need a way to get the latest from a specific platform.
            // The current getAllRawRates(symbolPrefix) might be too broad if "PF1_USDTRY" is passed as prefix.
            // Let's adjust to fetch specific rates if the symbol implies a platform.
            
            // This logic assumes sourceSymbolOrPrefix is a specific rate name like "PF1_USDTRY"
            // and RateCache can retrieve it directly or via a refined getAllRawRates.
            // For simplicity, if "PF1_USDTRY" is a required symbol, we expect it in the cache.
            // The current `getAllRawRates` takes a prefix. If `sourceSymbolOrPrefix` is "PF1_USDTRY",
            // it will look for keys like "rate:raw:*PF1_USDTRY*". This should work.
            Map<String, Rate> ratesForSymbol = rateCache.getAllRawRates(sourceSymbolOrPrefix);

            if (ratesForSymbol.isEmpty()) {
                logger.warn("Kaynak sembol için kur bulunamadı: {}", sourceSymbolOrPrefix);
                // Continue to gather other rates, the check for missing rates is done before calling calculate.
            } else {
                // If getAllRawRates returns multiple rates for a prefix, we need to decide which one to use.
                // For now, it returns a map of platformName to Rate.
                // The calculation strategy will then pick the specific ones it needs by platform name.
                // Or, if required symbols are specific like "PF1_USDTRY", then ratesForSymbol should contain that.
                sourceRates.putAll(ratesForSymbol);
            }
        }
        
        return sourceRates;
    }
}
