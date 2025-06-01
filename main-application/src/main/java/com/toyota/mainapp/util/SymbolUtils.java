package com.toyota.mainapp.util;

import lombok.extern.slf4j.Slf4j;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sembol formatlarını standartlaştırmak için yardımcı sınıf.
 * İki tip sembol formatı vardır:
 * 1. Ham Kurlar (Provider'dan Gelen): PROVIDERADI_BAZDOVIZKARŞITDOVIZ (örn: PF1_USDTRY, RESTPROVIDER_EURUSD)
 * 2. Hesaplanmış Kurlar: BAZDOVIZKARŞITDOVIZ_HESAPLAMATİPİ (örn: USDTRY_AVG, EURUSD_DIRECT, EURTRY_CROSS)
 */
@Slf4j
public class SymbolUtils {

    // USDTRY veya USD/TRY formatındaki semboller için regex
    private static final Pattern CURRENCY_PAIR_PATTERN = Pattern.compile("([A-Z]{3})/?([A-Z]{3})");
    
    // PROVIDERADI_BAZDOVIZKARŞITDOVIZ formatındaki semboller için regex (örn: PF1_USDTRY)
    private static final Pattern RAW_RATE_PATTERN = Pattern.compile("([A-Za-z0-9]+)_([A-Z]{3})/?([A-Z]{3})");
    
    // BAZDOVIZKARŞITDOVIZ_HESAPLAMATİPİ formatındaki semboller için regex (örn: USDTRY_AVG)
    private static final Pattern CALCULATED_RATE_PATTERN = Pattern.compile("([A-Z]{3})/?([A-Z]{3})_([A-Z]+)");
    
    /**
     * Sembolden temel sembolü türetir (sağlayıcıyı ve hesaplama tipi soneklerini kaldırır)
     * Örn: "PF1_USDTRY" -> "USDTRY" veya "USDTRY_AVG" -> "USDTRY" veya "USD/TRY" -> "USDTRY"
     */
    public static String deriveBaseSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return "";
        }
        
        // Ham kur formatı kontrolü (PROVIDERADI_BAZDOVIZKARŞITDOVIZ)
        Matcher rawRateMatcher = RAW_RATE_PATTERN.matcher(symbol);
        if (rawRateMatcher.matches()) {
            String baseCurrency = rawRateMatcher.group(2);
            String quoteCurrency = rawRateMatcher.group(3);
            return baseCurrency + quoteCurrency;
        }
        
        // Hesaplanmış kur formatı kontrolü (BAZDOVIZKARŞITDOVIZ_HESAPLAMATİPİ)
        Matcher calculatedRateMatcher = CALCULATED_RATE_PATTERN.matcher(symbol);
        if (calculatedRateMatcher.matches()) {
            String baseCurrency = calculatedRateMatcher.group(1);
            String quoteCurrency = calculatedRateMatcher.group(2);
            return baseCurrency + quoteCurrency;
        }
        
        // Para birimi çifti formatı (USDTRY veya USD/TRY)
        Matcher currencyPairMatcher = CURRENCY_PAIR_PATTERN.matcher(symbol);
        if (currencyPairMatcher.matches()) {
            String baseCurrency = currencyPairMatcher.group(1);
            String quoteCurrency = currencyPairMatcher.group(2);
            return baseCurrency + quoteCurrency;
        }
        
        // Eğer hiçbir formata uymuyorsa, sembolü olduğu gibi döndür
        log.warn("Sembol formatı tanınmadı, olduğu gibi döndürülüyor: {}", symbol);
        return symbol;
    }
    
    /**
     * Temel sembolden eğik çizgili formatı üretir
     * Örn: "USDTRY" -> "USD/TRY"
     */
    public static String formatWithSlash(String baseSymbol) {
        if (baseSymbol == null || baseSymbol.length() != 6) {
            return baseSymbol;
        }
        
        return baseSymbol.substring(0, 3) + "/" + baseSymbol.substring(3);
    }
    
    /**
     * Eğik çizgili sembolden temel sembolü üretir
     * Örn: "USD/TRY" -> "USDTRY"
     */
    public static String removeSlash(String slashedSymbol) {
        if (slashedSymbol == null) {
            return null;
        }
        return slashedSymbol.replace("/", "");
    }
    
    /**
     * Bir sembolün format türünü belirler
     */
    public static SymbolFormatType determineSymbolFormat(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return SymbolFormatType.UNKNOWN;
        }
        
        if (RAW_RATE_PATTERN.matcher(symbol).matches()) {
            return SymbolFormatType.RAW_RATE;
        }
        
        if (CALCULATED_RATE_PATTERN.matcher(symbol).matches()) {
            return SymbolFormatType.CALCULATED_RATE;
        }
        
        if (CURRENCY_PAIR_PATTERN.matcher(symbol).matches()) {
            return SymbolFormatType.CURRENCY_PAIR;
        }
        
        return SymbolFormatType.UNKNOWN;
    }
    
    /**
     * Ham kur formatından sağlayıcı adını çıkarır
     * Örn: "PF1_USDTRY" -> "PF1"
     */
    public static String extractProviderName(String rawRateSymbol) {
        if (rawRateSymbol == null || rawRateSymbol.isEmpty()) {
            return "";
        }
        
        Matcher matcher = RAW_RATE_PATTERN.matcher(rawRateSymbol);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        
        // Eğer format uyuşmazsa null dön
        return null;
    }
    
    /**
     * Hesaplanmış kur formatından hesaplama tipini çıkarır
     * Örn: "USDTRY_AVG" -> "AVG"
     */
    public static String extractCalculationType(String calculatedRateSymbol) {
        if (calculatedRateSymbol == null || calculatedRateSymbol.isEmpty()) {
            return "";
        }
        
        Matcher matcher = CALCULATED_RATE_PATTERN.matcher(calculatedRateSymbol);
        if (matcher.matches()) {
            return matcher.group(3);
        }
        
        // Eğer format uyuşmazsa null dön
        return null;
    }
    
    /**
     * Alternative symbol check - checks if two symbols represent the same currency pair
     * regardless of format (e.g., "EURTRY" and "EUR/TRY")
     * @param symbol1 First symbol
     * @param symbol2 Second symbol 
     * @return true if symbols represent the same currency pair
     */
    public static boolean symbolsEquivalent(String symbol1, String symbol2) {
        if (symbol1 == null || symbol2 == null) {
            return false;
        }
        
        // First try direct comparison
        if (symbol1.equals(symbol2)) {
            return true;
        }
        
        // Try with/without slash comparison
        String symbol1Base = removeSlash(symbol1);
        String symbol2Base = removeSlash(symbol2);
        return symbol1Base.equals(symbol2Base);
    }
    
    /**
     * Ham kur formatı oluşturur
     * Örn: "PF1", "USDTRY" -> "PF1_USDTRY"
     */
    public static String createRawRateSymbol(String providerName, String baseSymbol) {
        return providerName + "_" + baseSymbol;
    }
    
    /**
     * Hesaplanmış kur formatı oluşturur
     * Örn: "USDTRY", "AVG" -> "USDTRY_AVG"
     */
    public static String createCalculatedRateSymbol(String baseSymbol, String calculationType) {
        return baseSymbol + "_" + calculationType;
    }
    
    /**
     * Check if a symbol is a cross rate of specific currencies
     * 
     * @param symbol Symbol to check
     * @param baseCurrency Base currency to check for
     * @param quoteCurrency Quote currency to check for
     * @return true if the symbol represents a cross rate of the specified currencies
     */
    public static boolean isCrossRateOf(String symbol, String baseCurrency, String quoteCurrency) {
        if (symbol == null) return false;
        
        String normalized = symbol.toUpperCase().replace("/", "");
        return normalized.contains(baseCurrency.toUpperCase()) && 
               normalized.contains(quoteCurrency.toUpperCase());
    }
    
    /**
     * Generate all possible format variations of a symbol for lookups
     * 
     * @param symbol Original symbol
     * @return List of possible format variations
     */
    public static List<String> generateSymbolVariants(String symbol) {
        List<String> variants = new ArrayList<>();
        
        if (symbol == null || symbol.isEmpty()) {
            return variants;
        }
        
        // Add original
        variants.add(symbol);
        
        // Handle calc_rate: prefix
        if (symbol.startsWith("calc_rate:")) {
            String unprefixed = symbol.substring("calc_rate:".length());
            variants.add(unprefixed);
            
            // Add slashed/unslashed versions of unprefixed
            if (!unprefixed.contains("/")) {
                variants.add(formatWithSlash(unprefixed));
            } else {
                variants.add(removeSlash(unprefixed));
            }
        } else {
            // Add prefixed version
            variants.add("calc_rate:" + symbol);
            
            // Add slashed/unslashed versions
            if (!symbol.contains("/")) {
                String slashed = formatWithSlash(symbol);
                variants.add(slashed);
                variants.add("calc_rate:" + slashed);
            } else {
                String unslashed = removeSlash(symbol);
                variants.add(unslashed);
                variants.add("calc_rate:" + unslashed);
            }
        }
        
        // Handle _AVG suffix variants
        if (symbol.endsWith("_AVG")) {
            String baseSymbol = symbol.substring(0, symbol.length() - 4);
            variants.addAll(generateSymbolVariants(baseSymbol).stream()
                .map(s -> s + "_AVG")
                .collect(Collectors.toList()));
        }
        
        return variants;
    }
    
    /**
     * Sembol format türleri
     */
    public enum SymbolFormatType {
        RAW_RATE,           // PROVIDERADI_BAZDOVIZKARŞITDOVIZ (örn: PF1_USDTRY)
        CALCULATED_RATE,    // BAZDOVIZKARŞITDOVIZ_HESAPLAMATİPİ (örn: USDTRY_AVG)
        CURRENCY_PAIR,      // BAZDOVIZKARŞITDOVIZ veya BAZDOVIZ/KARŞITDOVIZ (örn: USDTRY veya USD/TRY)
        UNKNOWN             // Bilinmeyen format
    }
}