package com.toyota.mainapp.util;

import lombok.extern.slf4j.Slf4j;
import java.util.*;

/**
 * Cross rate hesaplama logic'i
 * Business Rule: USD bazlı cross rate calculation
 */
@Slf4j
public final class CrossRateUtils {
    
    // BASE CURRENCY for cross calculations - Configuration'dan gelecek
    private static final String BASE_CURRENCY = "USD";
    
    // Cross rate pairs - Configuration'dan gelecek
    private static final Map<String, List<String>> CROSS_RATE_PAIRS = Map.of(
        "GBPTRY", List.of("GBPUSD", "USDTRY"),
        "EURTRY", List.of("EURUSD", "USDTRY"),
        "GBPEUR", List.of("GBPUSD", "EURUSD")  // Bonus: EUR cross rate
    );
    
    private CrossRateUtils() {
        // Utility class
    }
    
    /**
     * Symbol'ün cross rate olup olmadığını kontrol et
     */
    public static boolean isCrossRateSymbol(String symbol) {
        String normalized = SymbolUtils.normalizeSymbol(symbol);
        return CROSS_RATE_PAIRS.containsKey(normalized);
    }
    
    /**
     * Cross rate için gerekli dependency symbol'leri al
     */
    public static List<String> getCrossRateDependencies(String crossRateSymbol) {
        String normalized = SymbolUtils.normalizeSymbol(crossRateSymbol);
        List<String> dependencies = CROSS_RATE_PAIRS.get(normalized);
        
        if (dependencies == null) {
            log.warn("Cross rate dependencies bulunamadı: {}", crossRateSymbol);
            return Collections.emptyList();
        }
        
        log.debug("Cross rate dependencies for {}: {}", crossRateSymbol, dependencies);
        return new ArrayList<>(dependencies);
    }
    
    /**
     * Available calculated rates'den cross rate calculation yapılabilir mi?
     */
    public static boolean canCalculateCrossRate(String crossRateSymbol, Set<String> availableCalculatedRates) {
        List<String> dependencies = getCrossRateDependencies(crossRateSymbol);
        
        if (dependencies.isEmpty()) {
            return false;
        }
        
        // Tüm dependency'ler AVG format ile mevcut mu?
        for (String dependency : dependencies) {
            String avgDependency = dependency + "_AVG";
            if (!availableCalculatedRates.contains(avgDependency)) {
                log.debug("Cross rate dependency missing: {} for {}", avgDependency, crossRateSymbol);
                return false;
            }
        }
        
        log.debug("Cross rate calculation possible for: {}", crossRateSymbol);
        return true;
    }
    
    /**
     * Possible cross rates'i available calculated rates'den generate et
     */
    public static List<String> generatePossibleCrossRates(Set<String> availableCalculatedRates) {
        List<String> possibleCrossRates = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : CROSS_RATE_PAIRS.entrySet()) {
            String crossRateSymbol = entry.getKey();
            
            if (canCalculateCrossRate(crossRateSymbol, availableCalculatedRates)) {
                possibleCrossRates.add(crossRateSymbol);
            }
        }
        
        log.debug("Possible cross rates: {}", possibleCrossRates);
        return possibleCrossRates;
    }
}