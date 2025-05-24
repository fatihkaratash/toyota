package com.toyota.mainapp.aggregator;

import com.toyota.mainapp.calculator.RateCalculatorService;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * İki yönlü pencere bazlı kur verisi toplayıcı.
 */
@Component
@Slf4j
public class TwoWayWindowAggregator {

    // Use field injection to break circular dependency
    @Autowired
    private RateCalculatorService rateCalculatorService;
    
    private final TaskScheduler taskScheduler;

    public TwoWayWindowAggregator(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
        log.info("TwoWayWindowAggregator initialized");
    }
    
    // Konfigürasyondan beklenen sağlayıcılar bilgisi alınır
    private final Map<String, List<String>> expectedProvidersConfig = new ConcurrentHashMap<>();
    
    // Zaman penceresi konfigürasyonu (milisaniye cinsinden)
    @Value("${app.aggregator.max-time-skew-ms:3000}")
    private long maxTimeSkewMs;
    
    // İç pencere yapısı: baseSymbol -> (providerName -> BaseRateDto)
    private final Map<String, Map<String, BaseRateDto>> window = new ConcurrentHashMap<>();
    
    @Value("${app.aggregator.window-cleanup-interval-ms:60000}")
    private long windowCleanupIntervalMs;
    
    /**
     * Initialize with default provider configuration and start scheduled tasks
     */
    @PostConstruct
    public void initializeDefaultConfig() {
        // Initialize with provider names that match what you're actually using
        List<String> usdTryProviders = List.of("RESTProvider1", "TCPProvider2");
        expectedProvidersConfig.put("USDTRY", usdTryProviders);
        
        List<String> eurUsdProviders = List.of("RESTProvider1", "TCPProvider2");
        expectedProvidersConfig.put("EURUSD", eurUsdProviders);
        
        List<String> gbpUsdProviders = List.of("RESTProvider1", "TCPProvider2"); 
        expectedProvidersConfig.put("GBPUSD", gbpUsdProviders);
        
        log.info("TwoWayWindowAggregator initialized with default configuration for currencies with providers: {}", 
                String.join(", ", usdTryProviders));
        
        // Schedule periodic cleanup using Spring's TaskScheduler
        taskScheduler.scheduleAtFixedRate(
            this::cleanupStaleWindows,
            Duration.ofMillis(windowCleanupIntervalMs)
        );
        log.info("Window cleanup scheduled every {} ms", windowCleanupIntervalMs);
    }

    /**
     * Accept a new rate and process it through the aggregator
     */
    public void accept(BaseRateDto baseRateDto) {
        if (baseRateDto == null) {
            log.warn("Toplayıcıya null BaseRateDto geldi, işlem yapılmıyor");
            return;
        }
        
        // Check if this is a raw rate - we only process raw rates
        if (baseRateDto.getRateType() != RateType.RAW) {
            log.debug("RAW olmayan kur toplayıcıya geldi, işlenmiyor: {}", baseRateDto.getRateType());
            return;
        }
        
        // Extract the base symbol from provider-specific symbol
        String providerSymbol = baseRateDto.getSymbol();
        String providerName = baseRateDto.getProviderName();
        
        // Derive base symbol (e.g., "PF1_USDTRY" -> "USDTRY")
        String baseSymbol = deriveBaseSymbol(providerSymbol);
        
        log.info("Toplayıcı kur aldı: sağlayıcı={}, orijinal sembol={}, temelSembol={}",
                providerName, providerSymbol, baseSymbol);
        
        // Add debug info about expected providers
        List<String> expectedProviders = getExpectedProviders(baseSymbol);
        log.debug("Beklenen sağlayıcılar {} için: {}", baseSymbol, expectedProviders);
        
        // Save to window structure
        window.computeIfAbsent(baseSymbol, k -> new ConcurrentHashMap<>())
              .put(providerName, baseRateDto);
        
        // Debug log for current window state
        Map<String, BaseRateDto> currentBucket = window.getOrDefault(baseSymbol, new ConcurrentHashMap<>());
        int currentCount = currentBucket.size();
        
        log.info("Window state for {}: Collected {}/{} providers. Current providers: {}, Expected providers: {}", 
                baseSymbol, currentCount, expectedProviders.size(),
                String.join(", ", currentBucket.keySet()),
                String.join(", ", expectedProviders));
        
        // Check if we can calculate with current window state
        checkWindowAndCalculate(baseSymbol);
    }
    
    /**
     * Bir temel sembol için pencere, hesaplama kriterlerini karşılıyor mu kontrol eder
     * ve karşılıyorsa hesaplamayı tetikler - basitleştirilmiş sürüm
     */
     private void checkWindowAndCalculate(String baseSymbol) {
        Map<String, BaseRateDto> currentSymbolBucketByProviderName = window.get(baseSymbol); // This is Map<providerName, BaseRateDto>
        if (currentSymbolBucketByProviderName == null || currentSymbolBucketByProviderName.isEmpty()) {
            return;
        }
        
        List<String> expectedProviderBaseNames = getExpectedProviders(baseSymbol);
        if (expectedProviderBaseNames.isEmpty()) {
            log.warn("{} için beklenen sağlayıcı yapılandırması bulunamadı.", baseSymbol);
            return;
        }

        // Ensure we only consider rates from the expected providers for this baseSymbol
        Map<String, BaseRateDto> relevantRatesByProviderName = new HashMap<>();
        boolean allExpectedProvidersPresent = true;
        for (String providerName : expectedProviderBaseNames) {
            if (currentSymbolBucketByProviderName.containsKey(providerName)) {
                relevantRatesByProviderName.put(providerName, currentSymbolBucketByProviderName.get(providerName));
            } else {
                allExpectedProvidersPresent = false;
                break;
            }
        }

        if (allExpectedProvidersPresent && relevantRatesByProviderName.size() == expectedProviderBaseNames.size()) {
            log.info("Tüm beklenen sağlayıcı grupları ({}) {} için veri gönderdi: {} - İşleme başlanabilir",
                    String.join(", ",expectedProviderBaseNames), baseSymbol, String.join(", ", relevantRatesByProviderName.keySet()));
            
            // isTimeSkewAcceptable expects a map keyed by provider name
            if (isTimeSkewAcceptable(relevantRatesByProviderName, expectedProviderBaseNames)) { 
                // Construct the map for RateCalculatorService, keyed by providerSpecificSymbol
                Map<String, BaseRateDto> ratesForCalculatorService = relevantRatesByProviderName.values().stream()
                    .collect(Collectors.toMap(
                        BaseRateDto::getSymbol, // Key: providerSpecificSymbol
                        rate -> rate,           // Value: BaseRateDto
                        (rate1, rate2) -> {     // Merge function for rare duplicate providerSpecificSymbol
                            log.warn("Duplicate providerSpecificSymbol {} encountered for baseSymbol {}. Using the first one.", rate1.getSymbol(), baseSymbol);
                            return rate1;
                        }
                    ));

                triggerCalculation(ratesForCalculatorService); 
                
                // Remove the processed rates from the window using providerName
                for (String providerName : expectedProviderBaseNames) {
                    currentSymbolBucketByProviderName.remove(providerName);
                }
                if (currentSymbolBucketByProviderName.isEmpty()) {
                    window.remove(baseSymbol);
                }
                log.info("Pencere {} için temizlendi ve hesaplama tetiklendi.", baseSymbol);
            } else {
                log.warn("Zaman kayması {} için çok yüksek, hesaplama atlandı.", baseSymbol);
            }
        }
    }
    /**
     * Eski pencere verilerini temizle - zamanla oluşabilecek hafıza sızıntılarını önler
     */
    private void cleanupStaleWindows() {
        try {
            log.debug("Eski pencere verileri temizleniyor...");
            long currentTime = System.currentTimeMillis();
            long staleCutoffTime = currentTime - (maxTimeSkewMs * 10); // 10 kat daha uzun bir pencere kullanımı
            
            int cleanedSymbols = 0;
            int cleanedRates = 0;
            
            for (Map.Entry<String, Map<String, BaseRateDto>> entry : window.entrySet()) {
                String symbol = entry.getKey();
                Map<String, BaseRateDto> providers = entry.getValue();
                
                // Eski kurları temizle
                for (Iterator<Map.Entry<String, BaseRateDto>> it = providers.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, BaseRateDto> providerEntry = it.next();
                    BaseRateDto rate = providerEntry.getValue();
                    
                    // Zaman damgası null veya çok eski olan kurları temizle
                    if (rate.getTimestamp() == null || rate.getTimestamp() < staleCutoffTime) {
                        it.remove();
                        cleanedRates++;
                        log.debug("{} sembolü için {} sağlayıcısından eski kur temizlendi", 
                                 symbol, providerEntry.getKey());
                    }
                }
                
                // Boş kalan sembol sepetiyle istemiyoruz, temizle
                if (providers.isEmpty()) {
                    window.remove(symbol);
                    cleanedSymbols++;
                    log.debug("{} sembolü için boş pencere temizlendi", symbol);
                }
            }
            
            if (cleanedRates > 0 || cleanedSymbols > 0) {
                log.info("Pencere temizleme tamamlandı: {} sembol ve {} kur temizlendi", 
                        cleanedSymbols, cleanedRates);
            }
        } catch (Exception e) {
            log.error("Pencere temizleme sırasında hata oluştu", e);
        }
    }
    
    /**
     * Kurlar arasındaki zaman kaymasının kabul edilebilir sınırlar içinde olup olmadığını kontrol eder
     */
    private boolean isTimeSkewAcceptable(Map<String, BaseRateDto> symbolBucket, List<String> providers) {
        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = Long.MIN_VALUE;
        
        // Min ve max zaman damgalarını bul
        for (String provider : providers) {
            BaseRateDto rate = symbolBucket.get(provider);
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
    private void triggerCalculation(Map<String, BaseRateDto> newlyCompletedRawRatesInWindow) { // Parameter changed
        try {
            // Pass the map where keys are providerSpecificSymbol
            log.debug("Triggering calculation with {} raw rates: {}", newlyCompletedRawRatesInWindow.size(), newlyCompletedRawRatesInWindow.keySet());
            rateCalculatorService.processWindowCompletion(newlyCompletedRawRatesInWindow);
        } catch (Exception e) {
            log.error("Hesaplama tetiklenirken hata: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sağlayıcıya özgü sembolden temel sembolü türet
     */
    private String deriveBaseSymbol(String providerSymbol) {
        if (providerSymbol == null) {
            return null;
        }
        
        int underscoreIndex = providerSymbol.indexOf('_');
        String baseSymbol = underscoreIndex > 0 ? providerSymbol.substring(underscoreIndex + 1) : providerSymbol;
        return baseSymbol;
    }
    
    /**
     * Bir temel sembol için beklenen sağlayıcıların listesini al
     */
    private List<String> getExpectedProviders(String baseSymbol) {
        return expectedProvidersConfig.getOrDefault(baseSymbol, Collections.emptyList());
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
