package com.toyota.mainapp.aggregator;

import com.toyota.mainapp.calculator.RateCalculatorService;
import com.toyota.mainapp.dto.RawRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * İki yönlü pencere bazlı kur verisi toplayıcı.
 * Farklı sağlayıcılardan gelen ham kur verilerini bir zaman penceresi içinde toplar
 * ve hesaplama için uygun koşullar sağlandığında hesaplamaları tetikler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TwoWayWindowAggregator {

    private final RateCalculatorService rateCalculatorService;
    
    // Konfigürasyondan beklenen sağlayıcılar bilgisi alınır
    private final Map<String, List<String>> expectedProvidersConfig = new ConcurrentHashMap<>();
    
    // Zaman penceresi konfigürasyonu (milisaniye cinsinden)
    @Value("${app.aggregator.max-time-skew-ms:3000}")
    private long maxTimeSkewMs;
    
    // İç pencere yapısı: baseSymbol -> (providerName -> RawRateDto)
    private final Map<String, Map<String, RawRateDto>> window = new ConcurrentHashMap<>();
    
    /**
     * Initialize with default provider configuration
     */
    @PostConstruct
    public void initializeDefaultConfig() {
        // Initialize default configuration for USDTRY
        List<String> usdTryProviders = List.of("TCPProvider2", "RESTProvider1");
        expectedProvidersConfig.put("USDTRY", usdTryProviders);
        
        // Initialize default configuration for EURUSD
        List<String> eurUsdProviders = List.of("TCPProvider2", "RESTProvider1");
        expectedProvidersConfig.put("EURUSD", eurUsdProviders);
        
        log.info("TwoWayWindowAggregator initialized with default configuration: USDTRY and EURUSD with providers: {}", 
                usdTryProviders);
    }

    /**
     * Yeni bir ham kur verisi alır ve toplayıcı tarafından işlenmesini sağlar
     */
    public void accept(RawRateDto rawRateDto) {
        if (rawRateDto == null) {
            log.warn("Toplayıcıya null RawRateDto geldi, işlem yapılmıyor");
            return;
        }
        
        // Sağlayıcıya özgü sembolden temel sembolü çıkar
        String providerSymbol = rawRateDto.getSymbol();
        String providerName = rawRateDto.getProviderName();
        
        // Temel sembolü türet (örn. "PF1_USDTRY" -> "USDTRY")
        String baseSymbol = deriveBaseSymbol(providerSymbol);
        
        log.info("Toplayıcı kur aldı: sağlayıcı={}, orijinal sembol={}, temelSembol={}",
                providerName, providerSymbol, baseSymbol);
        
        // Pencere yapısına kaydet
        window.computeIfAbsent(baseSymbol, k -> new ConcurrentHashMap<>())
              .put(providerName, rawRateDto);
              
        // Debug log for current window state
        Map<String, RawRateDto> currentBucket = window.getOrDefault(baseSymbol, new ConcurrentHashMap<>());
        int currentCount = currentBucket.size();
        List<String> expectedProviders = getExpectedProviders(baseSymbol);
        
        log.info("Window state for {}: Collected {}/{} providers. Current providers: {}, Expected providers: {}", 
                baseSymbol, currentCount, expectedProviders.size(),
                String.join(", ", currentBucket.keySet()),
                String.join(", ", expectedProviders));
        
        // Mevcut pencere durumuyla hesaplama yapılabilir mi kontrol et
        checkWindowAndCalculate(baseSymbol);
    }
    
    /**
     * Bir temel sembol için pencere, hesaplama kriterlerini karşılıyor mu kontrol eder
     * ve karşılıyorsa hesaplamayı tetikler
     */
    private void checkWindowAndCalculate(String baseSymbol) {
        // Bu temel sembol için sepeti al
        Map<String, RawRateDto> symbolBucket = window.get(baseSymbol);
        if (symbolBucket == null || symbolBucket.isEmpty()) {
            log.debug("{} sembolü için boş sepet, hesaplanacak bir şey yok", baseSymbol);
            return;
        }
        
        // Bu sembol için beklenen sağlayıcıları al
        List<String> expectedProviders = getExpectedProviders(baseSymbol);
        if (expectedProviders.isEmpty()) {
            log.warn("{} için beklenen sağlayıcı yapılandırması yok, toplanamıyor", baseSymbol);
            return;
        }
        
        // Tüm beklenen sağlayıcılara sahip miyiz?
        if (symbolBucket.keySet().containsAll(expectedProviders)) {
            log.info("All expected providers for {} found: {} - Processing can start", 
                    baseSymbol, expectedProviders);
            
            // Kurlar arasındaki zaman kayması kabul edilebilir mi?
            if (isTimeSkewAcceptable(symbolBucket, expectedProviders)) {
                // Kabul edilebilir zaman penceresi içinde tüm verilere sahibiz, hesaplamayı tetikle
                log.info("{} için toplama kriterleri karşılandı, hesaplama tetikleniyor", baseSymbol);
                
                // Log detailed rate information before calculation
                symbolBucket.forEach((provider, rate) -> {
                    log.debug("Provider {} rate for {}: bid={}, ask={}, timestamp={}", 
                             provider, baseSymbol, rate.getBid(), rate.getAsk(), rate.getTimestamp());
                });
                
                triggerCalculation(baseSymbol, new HashMap<>(symbolBucket));
                
                // Hesaplamadan sonra sepeti temizle
                symbolBucket.clear();
                log.debug("Window cleared for {} after calculation", baseSymbol);
            } else {
                log.debug("{} için zaman kayması çok fazla, hesaplama erteleniyor", baseSymbol);
            }
        } else {
            log.debug("{} için daha fazla sağlayıcı bekleniyor. Mevcut: {}/{}. Sağlayıcılar: {}, Beklenenler: {}", 
                    baseSymbol, symbolBucket.size(), expectedProviders.size(),
                    String.join(", ", symbolBucket.keySet()),
                    String.join(", ", expectedProviders));
        }
    }
    
    /**
     * Kurlar arasındaki zaman kaymasının kabul edilebilir sınırlar içinde olup olmadığını kontrol eder
     */
    private boolean isTimeSkewAcceptable(Map<String, RawRateDto> symbolBucket, List<String> providers) {
        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = Long.MIN_VALUE;
        
        // Min ve max zaman damgalarını bul
        for (String provider : providers) {
            RawRateDto rate = symbolBucket.get(provider);
            if (rate != null && rate.getTimestamp() != null) {
                long timestamp = rate.getTimestamp();
                minTimestamp = Math.min(minTimestamp, timestamp);
                maxTimestamp = Math.max(maxTimestamp, timestamp);
            }
        }
        
        // Zaman kaymasını hesapla ve kabul edilebilir mi kontrol et
        long timeSkew = maxTimestamp - minTimestamp;
        boolean acceptable = timeSkew <= maxTimeSkewMs;
        
        log.debug("Kurlar için zaman kayması: {} ms, kabul edilebilir: {}", timeSkew, acceptable);
        return acceptable;
    }
    
    /**
     * Toplanan kurlar için hesaplamayı tetikle
     */
    private void triggerCalculation(String baseSymbol, Map<String, RawRateDto> symbolBucket) {
        try {
            rateCalculatorService.calculateFromAggregatedData(baseSymbol, symbolBucket);
        } catch (Exception e) {
            log.error("{} için hesaplama tetiklenirken hata: {}", baseSymbol, e.getMessage(), e);
        }
    }
    
    /**
     * Sağlayıcıya özgü sembolden temel sembolü türet
     */
    private String deriveBaseSymbol(String providerSymbol) {
        // Bu metot, adlandırma kuralınıza göre özelleştirilmelidir
        // Örnek: "PF1_USDTRY" -> "USDTRY"
        if (providerSymbol == null) {
            return null;
        }
        
        int underscoreIndex = providerSymbol.indexOf('_');
        String baseSymbol = underscoreIndex > 0 ? providerSymbol.substring(underscoreIndex + 1) : providerSymbol;
        log.debug("Derived base symbol: {} from provider symbol: {}", baseSymbol, providerSymbol);
        return baseSymbol;
    }
    
    /**
     * Bir temel sembol için beklenen sağlayıcıların listesini al
     */
    private List<String> getExpectedProviders(String baseSymbol) {
        // Gerçek uygulamada bu yapılandırmadan gelirdi
        return expectedProvidersConfig.computeIfAbsent(baseSymbol, k -> {
            // Varsayılan örnek yapılandırma
            if ("USDTRY".equals(baseSymbol)) {
                return List.of("TCPProvider2", "RESTProvider1");
            }
            return List.of();
        });
    }
    
    /**
     * Yapılandırma ile başlatma
     */
    public void initialize(Map<String, List<String>> symbolProvidersConfig) {
        if (symbolProvidersConfig != null) {
            this.expectedProvidersConfig.putAll(symbolProvidersConfig);
            log.info("Toplayıcı {} sembol yapılandırması ile başlatıldı", symbolProvidersConfig.size());
        }
    }
}
