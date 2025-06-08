package com.toyota.mainapp.util;

import lombok.extern.slf4j.Slf4j;
import com.toyota.mainapp.dto.model.BaseRateDto;
import java.util.*;

/**
 * ✅ SIMPLIFIED: Cross rate utility - basic functionality only
 * No complex dependency resolution - configuration should be explicit
 */
@Slf4j
public final class CrossRateUtils {
    
    // Basic cross rate pairs for fallback - configuration should override these
    private static final Map<String, List<String>> BASIC_CROSS_PAIRS = Map.of(
        "GBPTRY", List.of("GBPUSD", "USDTRY"),
        "EURTRY", List.of("EURUSD", "USDTRY")
    );
    
    private CrossRateUtils() {
        // Utility class
    }
    
    /**
     * ✅ SIMPLIFIED: Check if symbol is a known cross rate
     */
    public static boolean isCrossRateSymbol(String symbol) {
        if (symbol == null) return false;
        String normalized = SymbolUtils.normalizeSymbol(symbol);
        return BASIC_CROSS_PAIRS.containsKey(normalized);
    }
    
    /**
     * ✅ SIMPLIFIED: Get basic dependencies for cross rate
     */
    public static List<String> getCrossRateDependencies(String crossRateSymbol) {
        String normalized = SymbolUtils.normalizeSymbol(crossRateSymbol);
        return BASIC_CROSS_PAIRS.getOrDefault(normalized, Collections.emptyList());
    }
    
    /**
     * ✅ ENHANCED: Check if cross rate dependencies are available in snapshot
     * Aligned with CrossRateCalculationStage usage
     */
    public static boolean canCalculateFromSnapshot(String crossRateSymbol, 
                                                  Map<String, BaseRateDto> snapshotLookup) {
        List<String> dependencies = getCrossRateDependencies(crossRateSymbol);
        
        if (dependencies.isEmpty()) {
            return false;
        }
        
        for (String dependency : dependencies) {
            boolean found = false;
            
            // ✅ ENHANCED: Try comprehensive key formats for snapshot lookup
            String[] possibleKeys = {
                dependency,                              // "EURUSD"
                dependency + "_AVG",                     // "EURUSD_AVG"
                "CALC_" + dependency + "_AVG",           // "CALC_EURUSD_AVG"
                "CALC_" + dependency,                    // "CALC_EURUSD"
                dependency.replace("CALC_", ""),         // Remove CALC_ if present
                dependency.replace("_AVG", "") + "_AVG"  // Normalize _AVG
            };
            
            for (String key : possibleKeys) {
                if (snapshotLookup.containsKey(key)) {
                    found = true;
                    log.debug("✅ Cross dependency found: {} -> {}", dependency, key);
                    break;
                }
            }
            
            if (!found) {
                log.debug("❌ Missing cross dependency: {} (tried: {})", 
                        dependency, String.join(", ", possibleKeys));
                return false;
            }
        }
        
        return true;
    }
}