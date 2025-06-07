package com.toyota.mainapp.util;

import lombok.extern.slf4j.Slf4j;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

/**
 * Symbol işlemleri için utility sınıfı
 * TEK FORMAT: USDTRY (6 karakter, büyük harf, underscore yok)
 */
@Slf4j
public final class SymbolUtils {

    // TEK PATTERN - Sadece 6 karakter currency pair
    private static final Pattern CURRENCY_PAIR_PATTERN = Pattern.compile("^[A-Z]{6}$");

    private SymbolUtils() {
        // Utility class - instantiation engellendi
    }

    /**
     * Symbol'ü standart 6-karakter formatına normalize et: USDTRY
     */
    public static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            log.debug("Null veya boş symbol normalize edildi: ''");
            return "";
        }

        String normalized = symbol.trim().toUpperCase();

        // Provider prefix'ini kaldır (PF1_USDTRY -> USDTRY)
        if (normalized.contains("_")) {
            String[] parts = normalized.split("_");
            if (parts.length >= 2) {
                // Calculation suffix varsa kaldır (USDTRY_AVG -> USDTRY)
                String symbolPart = parts[parts.length - 1];
                if (symbolPart.equals("AVG") || symbolPart.equals("CROSS") || symbolPart.equals("CALC")) {
                    // Suffix ise, önceki parça symbol'dür
                    if (parts.length >= 3) {
                        normalized = parts[parts.length - 2];
                    } else {
                        // PF1_AVG gibi durumlar için ilk parçayı al
                        normalized = parts[0];
                    }
                } else {
                    // Normal provider_symbol formatı
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

        // Son kontrol ve log
        if (!normalized.isEmpty() && !isValidSymbol(normalized)) {
            log.warn("Symbol normalize edildi ama geçersiz format: '{}' -> '{}'", symbol, normalized);
        }

        return normalized;
    }

    /**
     * Symbol'ün doğru 6-karakter formatında olup olmadığını kontrol et
     */
    public static boolean isValidSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        return CURRENCY_PAIR_PATTERN.matcher(symbol).matches();
    }

    /**
     * Base currency'yi al (USDTRY -> USD)
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
     */
    public static String getQuoteCurrency(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (isValidSymbol(normalized)) {
            return normalized.substring(3, 6);
        }
        return "";
    }

    /**
     * Symbol variants generate et - cross rate ve cache lookup için
     * Tek format'ta sadece temel variations
     */
    public static List<String> generateSymbolVariants(String symbol) {
        List<String> variants = new ArrayList<>();

        if (symbol == null || symbol.trim().isEmpty()) {
            return variants;
        }

        String normalized = normalizeSymbol(symbol);

        // 1. Normalized format (temel)
        if (isValidSymbol(normalized)) {
            variants.add(normalized);

            // 2. AVG suffix
            variants.add(normalized + "_AVG");

            // 3. CROSS suffix
            variants.add(normalized + "_CROSS");

            // 4. CALC suffix
            variants.add(normalized + "_CALC");
        }

        // 5. Original format (eğer farklıysa)
        String originalNormalized = symbol.trim().toUpperCase();
        if (!originalNormalized.equals(normalized) && !variants.contains(originalNormalized)) {
            variants.add(originalNormalized);
        }

        log.debug("Generated {} variants for symbol '{}': {}",
                variants.size(), symbol, String.join(", ", variants));

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
     */
    public static boolean isCrossRate(String symbol) {
        if (symbol == null) return false;
        
        String normalized = normalizeSymbol(symbol);
        
        // Check for TRY-based cross rates (EURTRY, GBPTRY, etc.)
        return normalized.endsWith("TRY") && !normalized.equals("USDTRY");
    }

    /**
     * ✅ NEW: Determine calculation type from symbol or metadata
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
}