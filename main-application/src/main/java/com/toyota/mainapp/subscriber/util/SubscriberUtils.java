package com.toyota.mainapp.subscriber.util;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class SubscriberUtils {

    private SubscriberUtils() {
        // Utility class
    }

    public static String[] getSymbols(Map<String, Object> config, String providerName) {
        if (config == null || !config.containsKey("symbols")) {
            log.warn("[{}] Yapılandırmada 'symbols' anahtarı bulunamadı", providerName);
            return new String[0];
        }
        
        Object symbolsObj = config.get("symbols");
        log.debug("[{}] Yapılandırmadan alınan ham symbols nesnesi: {}", 
                providerName, symbolsObj);
        
        List<String> parsedSymbols = new ArrayList<>();
        
        if (symbolsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> symbolsList = (List<String>) symbolsObj;
            for (String item : symbolsList) {
                if (item != null) {
                    String[] parts = item.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            parsedSymbols.add(trimmed);
                        }
                    }
                }
            }
        } 
        else if (symbolsObj instanceof String) {
            String symbolsStr = (String) symbolsObj;
            String[] parts = symbolsStr.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    parsedSymbols.add(trimmed);
                }
            }
        }
        else if (symbolsObj instanceof String[]) {
            for (String s : (String[]) symbolsObj) {
                if (s != null) {
                    String[] parts = s.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            parsedSymbols.add(trimmed);
                        }
                    }
                }
            }
        }
        
        log.info("[{}] Çözümlenen semboller: {}", providerName, parsedSymbols);
        return parsedSymbols.toArray(new String[0]);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getConfigValue(Map<String, Object> config, String key, T defaultValue) {
        if (config == null || !config.containsKey(key) || config.get(key) == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (defaultValue == null) { // Cannot check class if defaultValue is null
             if (value.getClass().isInstance(defaultValue)) { // This check is problematic if defaultValue is null
                return (T) value;
             } else { // Attempt conversion for common types if defaultValue is null, or handle as error
                 // This part needs careful consideration based on expected types if defaultValue can be null
                 // For now, assuming defaultValue helps determine the type.
                 // If T is String and value is Number, convert Number to String.
                 if (value instanceof Number && defaultValue == null) { // Example: if T could be String
                     // This is a placeholder, proper handling depends on T
                     // return (T) String.valueOf(value);
                 }
                 // Fallback or throw error if type is incompatible and defaultValue is null
                 return (T) value; // May cause ClassCastException if types mismatch and defaultValue is null
             }
        }

        if (value.getClass().isAssignableFrom(defaultValue.getClass())) {
            return (T) value;
        }
        // Handle type conversion for numbers if defaultValue is a number type but value is a different number type
        if (defaultValue instanceof Number && value instanceof Number) {
            Number numValue = (Number) value;
            if (defaultValue instanceof Integer) return (T) Integer.valueOf(numValue.intValue());
            if (defaultValue instanceof Long) return (T) Long.valueOf(numValue.longValue());
            if (defaultValue instanceof Double) return (T) Double.valueOf(numValue.doubleValue());
            if (defaultValue instanceof Float) return (T) Float.valueOf(numValue.floatValue());
        }
        // Handle conversion from String to Number if config stores numbers as strings
        if (defaultValue instanceof Number && value instanceof String) {
            try {
                String strValue = (String) value;
                if (defaultValue instanceof Integer) return (T) Integer.valueOf(strValue);
                if (defaultValue instanceof Long) return (T) Long.valueOf(strValue);
                if (defaultValue instanceof Double) return (T) Double.valueOf(strValue);
                if (defaultValue instanceof Float) return (T) Float.valueOf(strValue);
            } catch (NumberFormatException e) {
                log.warn("Could not parse string '{}' to number for key '{}'. Using default value '{}'.", value, key, defaultValue);
                return defaultValue;
            }
        }
        log.warn("Config value for key '{}' is of type {} but expected type {}. Using default value '{}'.",
                key, value.getClass().getName(), defaultValue.getClass().getName(), defaultValue);
        return defaultValue;
    }

    public static void sendRateStatus(PlatformCallback callback, String providerName, String symbol, 
                                      BaseRateDto.RateStatusEnum status, String statusMessage) {
        if (callback != null) {
            BaseRateDto statusRate = BaseRateDto.builder()
                .rateType(RateType.STATUS)
                .symbol(symbol)
                .providerName(providerName)
                .status(status)
                .statusMessage(statusMessage)
                .timestamp(System.currentTimeMillis())
                .build();
                
            callback.onRateStatus(providerName, statusRate);
        }
    }
}
