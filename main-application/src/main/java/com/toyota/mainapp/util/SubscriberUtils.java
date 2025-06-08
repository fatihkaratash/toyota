package com.toyota.mainapp.util;

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
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            log.warn("Config value type mismatch for key {}: expected {}, got {}", 
                    key, defaultValue.getClass().getSimpleName(), value.getClass().getSimpleName());
            return defaultValue;
        }
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
