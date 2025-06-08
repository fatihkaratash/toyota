package com.toyota.mainapp.util;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SymbolUtils {

    private static final Map<String, String> NORMALIZE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> VARIANTS_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Pattern CURRENCY_PAIR_PATTERN = Pattern.compile("^[A-Z]{6}$");

    private SymbolUtils() {
    }

    public static String normalizeSymbol(String symbol) {
        if (symbol == null) return null;
        String cached = NORMALIZE_CACHE.get(symbol);
        if (cached != null) {
            return cached;
        }

        String normalized = symbol.trim().toUpperCase().replace("/", "");

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

        if (normalized.startsWith("CALC_RATE:")) {
            normalized = normalized.substring("CALC_RATE:".length());
        }

        if (NORMALIZE_CACHE.size() < MAX_CACHE_SIZE) {
            NORMALIZE_CACHE.put(symbol, normalized);
        }

        return normalized;
    }

    public static boolean isValidSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        String normalized = normalizeSymbol(symbol);
        return normalized.length() >= 6 && normalized.matches("^[A-Z]+$");
    }

    public static String addSlash(String symbol) {
        if (symbol == null || symbol.length() < 6) return symbol;
        return symbol.substring(0, 3) + "/" + symbol.substring(3);
    }

    public static boolean symbolsEquivalent(String symbol1, String symbol2) {
        if (symbol1 == null || symbol2 == null) return false;
        return normalizeSymbol(symbol1).equals(normalizeSymbol(symbol2));
    }

    public static String generatePipelineId(BaseRateDto rate) {
        if (rate == null) return UUID.randomUUID().toString();
        return rate.getSymbol() + "_" + System.currentTimeMillis();
    }

    public static String generateSnapshotKey(BaseRateDto rate) {
        if (rate == null) return "";
        
        String symbol = rate.getSymbol();
        if (rate.getRateType() == RateType.RAW && rate.getProviderName() != null) {
            return rate.getProviderName() + "_" + symbol;
        }
        return symbol;
    }
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

    private static boolean isCrossRate(String symbol) {
        
        return !CURRENCY_PAIR_PATTERN.matcher(symbol).matches();
    }

}