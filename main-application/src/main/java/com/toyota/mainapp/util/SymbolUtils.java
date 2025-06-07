package com.toyota.mainapp.util;

import lombok.extern.slf4j.Slf4j;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Symbol işlemleri için utility sınıfı
 * ✅ ENHANCED: TEK FORMAT + Performance optimizations
 */
@Slf4j
public final class SymbolUtils {

    // ✅ PERFORMANCE: Cache for normalized symbols
    private static final Map<String, String> NORMALIZE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> VARIANTS_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    // TEK PATTERN - Sadece 6 karakter currency pair
    private static final Pattern CURRENCY_PAIR_PATTERN = Pattern.compile("^[A-Z]{6}$");

    private SymbolUtils() {
        // Utility class - instantiation engellendi
    }

    /**
     * ✅ ENHANCED: Symbol'ü standart formatına normalize et (cached)
     * ✅ ACTIVELY USED: Symbol normalization in MainCoordinatorService
     * Usage: normalizeSymbol() called for every incoming rate
     */
    public static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return "";
        }

        // ✅ PERFORMANCE: Check cache first
        String cached = NORMALIZE_CACHE.get(symbol);
        if (cached != null) {
            return cached;
        }

        String normalized = symbol.trim().toUpperCase();

        // Provider prefix'ini kaldır (PF1_USDTRY -> USDTRY)
        if (normalized.contains("_")) {
            String[] parts = normalized.split("_");
            if (parts.length >= 2) {
                String symbolPart = parts[parts.length - 1];
                if (symbolPart.equals("AVG") || symbolPart.equals("CROSS") || symbolPart.equals("CALC")) {
                    if (parts.length >= 3) {
                        normalized = parts[parts.length - 2];
                    } else {
                        normalized = parts[0];
                    }
                } else {
                    normalized = symbolPart;
                }
            }
        }

        // Slash'ı kaldır (USD/TRY -> USDTRY)
        normalized = normalized.replace("/", "");

        // calc_rate: prefix'ini kaldır
        if (normalized.startsWith("CALC_RATE:")) {
            normalized = normalized.substring("CALC_RATE:".length());
        }

        // ✅ PERFORMANCE: Cache result (with size limit)
        if (NORMALIZE_CACHE.size() < MAX_CACHE_SIZE) {
            NORMALIZE_CACHE.put(symbol, normalized);
        }

        return normalized;
    }

    /**
     * Symbol'ün doğru 6-karakter formatında olup olmadığını kontrol et
     * ✅ ACTIVELY USED: Validation in MainCoordinatorService
     * Usage: isValidSymbol() called after normalization
     */
    public static boolean isValidSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        return CURRENCY_PAIR_PATTERN.matcher(symbol).matches();
    }

    /**
     * Base currency'yi al (USDTRY -> USD)
     * ✅ ACTIVELY USED: Currency extraction for cross-rate calculations
     * Usage: getBaseCurrency(), getQuoteCurrency() in GroovyScriptCalculationStrategy
     */
    public static String getBaseCurrency(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (isValidSymbol(normalized)) {
            return normalized.substring(0, 3);
        }
        return "";
    }

    /**
     * Quote currency'yi al (USDTRY -> TRY)
     * ✅ ACTIVELY USED: Currency extraction for cross-rate calculations
     * Usage: getBaseCurrency(), getQuoteCurrency() in GroovyScriptCalculationStrategy
     */
    public static String getQuoteCurrency(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (isValidSymbol(normalized)) {
            return normalized.substring(3, 6);
        }
        return "";
    }

    /**
     * ✅ ENHANCED: Symbol variants generate et (cached)
     */
    public static List<String> generateSymbolVariants(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return List.of();
        }

        // ✅ PERFORMANCE: Check cache first
        List<String> cached = VARIANTS_CACHE.get(symbol);
        if (cached != null) {
            return cached;
        }

        List<String> variants = new ArrayList<>();
        String normalized = normalizeSymbol(symbol);

        if (isValidSymbol(normalized)) {
            variants.add(normalized);
            variants.add(normalized + "_AVG");
            variants.add(normalized + "_CROSS");
            variants.add(normalized + "_CALC");
        }

        String originalNormalized = symbol.trim().toUpperCase();
        if (!originalNormalized.equals(normalized) && !variants.contains(originalNormalized)) {
            variants.add(originalNormalized);
        }

        // ✅ PERFORMANCE: Cache result (with size limit)
        if (VARIANTS_CACHE.size() < MAX_CACHE_SIZE) {
            VARIANTS_CACHE.put(symbol, List.copyOf(variants));
        }

        return variants;
    }

    // GERİ EKLENEN METHODLAR - Backward compatibility için

    /*
     * @Deprecated
     * public static String deriveBaseSymbol(String symbol) {
     * return normalizeSymbol(symbol);
     * }
     */

    /**
     * İki symbol'ün eşdeğer olup olmadığını kontrol et
     */
    public static boolean symbolsEquivalent(String symbol1, String symbol2) {
        if (symbol1 == null && symbol2 == null) {
            return true;
        }
        if (symbol1 == null || symbol2 == null) {
            return false;
        }

        String normalized1 = normalizeSymbol(symbol1);
        String normalized2 = normalizeSymbol(symbol2);

        return normalized1.equals(normalized2);
    }

    /**
     * ✅ NEW: Check if symbol represents a cross rate (like EURTRY from USDTRY+EURUSD)
     * ✅ ACTIVELY USED: Cross rate detection in strategy selection
     * Usage: isCrossRate() in CalculationStrategyFactory
     */
    public static boolean isCrossRate(String symbol) {
        if (symbol == null) return false;
        
        String normalized = normalizeSymbol(symbol);
        
        // Check for TRY-based cross rates (EURTRY, GBPTRY, etc.)
        return normalized.endsWith("TRY") && !normalized.equals("USDTRY");
    }

    /**
     * ✅ NEW: Determine calculation type from symbol or metadata
     * ✅ ACTIVELY USED: Strategy type determination
     * Usage: determineCalculationType() in pipeline stages
     */
    public static String determineCalculationType(String symbol, String strategy) {
        if (symbol == null) return "UNKNOWN";
        
        String normalized = normalizeSymbol(symbol);
        
        // From strategy name
        if (strategy != null) {
            if (strategy.contains("average")) return "AVG";
            if (strategy.contains("cross") || strategy.contains("groovy")) return "CROSS";
        }
        
        // From symbol pattern
        if (normalized.contains("AVG")) return "AVG";
        if (normalized.contains("CROSS")) return "CROSS";
        if (isCrossRate(normalized)) return "CROSS";
        
        return "AVG"; // Default for calculated rates
    }

    /**
     * 
     * @Deprecated
     *             public static String formatWithSlash(String symbol) {
     *             String normalized = normalizeSymbol(symbol);
     *             if (isValidSymbol(normalized)) {
     *             return normalized.substring(0, 3) + "/" +
     *             normalized.substring(3);
     *             }
     *             return symbol;
     *             }
     */

    /**
     * Symbol'e slash ekle (USDTRY -> USD/TRY)
     */
    public static String addSlash(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (isValidSymbol(normalized)) {
            return normalized.substring(0, 3) + "/" + normalized.substring(3);
        }
        return symbol;
    }

    /**
     * Symbol'den slash kaldır (USD/TRY -> USDTRY)
     */
    public static String removeSlash(String symbol) {
        return normalizeSymbol(symbol);
    }

    /**
     * ✅ NEW: Clear symbol caches (for testing/monitoring)
     */
    public static void clearCaches() {
        NORMALIZE_CACHE.clear();
        VARIANTS_CACHE.clear();
        log.debug("🔄 SymbolUtils caches cleared");
    }

    /**
     * ✅ NEW: Get cache statistics
     */
    public static Map<String, Integer> getCacheStats() {
        return Map.of(
            "normalizeCacheSize", NORMALIZE_CACHE.size(),
            "variantsCacheSize", VARIANTS_CACHE.size()
        );
    }
}