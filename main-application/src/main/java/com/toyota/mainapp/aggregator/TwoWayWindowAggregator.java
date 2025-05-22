package com.toyota.mainapp.aggregator;

import com.toyota.mainapp.calculator.RateCalculatorService;
import com.toyota.mainapp.dto.RateMessageDto;
import com.toyota.mainapp.dto.payload.RawRatePayloadDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TwoWayWindowAggregator {

    // Kurları sembole göre toplama penceresi (örn., "USDTRY" -> {"Provider1" -> rateData1, "Provider2" -> rateData2})
    private final Map<String, Map<String, RawRatePayloadDto>> window = new ConcurrentHashMap<>();
    
    // Kur hesaplamalarını yapacak servis
    private final RateCalculatorService rateCalculatorService;
    
    // Sembole göre beklenen sağlayıcı konfigürasyonu
    private final Map<String, List<String>> expectedProvidersPerSymbol;
    
    // İzin verilen maksimum zaman farkı
    private final Duration maxTimeSkew;

    public TwoWayWindowAggregator(
            RateCalculatorService rateCalculatorService,
            @Value("${app.aggregator.max-time-skew-seconds:3}") int maxTimeSkewSeconds,
            AggregatorConfigService configService) {
        this.rateCalculatorService = rateCalculatorService;
        this.maxTimeSkew = Duration.ofSeconds(maxTimeSkewSeconds);
        this.expectedProvidersPerSymbol = configService.loadExpectedProvidersBySymbol();
        
        log.info("TwoWayWindowAggregator başlatıldı, maxTimeSkew: {} saniye, beklenen sağlayıcılar: {}", 
                maxTimeSkewSeconds, expectedProvidersPerSymbol);
    }

    @PostConstruct
    public void init() {
        log.info("TwoWayWindowAggregator servisi başlatıldı");
    }

    public void accept(RateMessageDto rateMessage) {
        if (rateMessage == null || !"RAW".equals(rateMessage.getRateType()) || rateMessage.getPayload() == null) {
            log.warn("Geçersiz kur mesajı alındı: {}", rateMessage);
            return;
        }

        try {
            RawRatePayloadDto payload = (RawRatePayloadDto) rateMessage.getPayload();
            String symbolKey = extractBaseSymbol(payload.getSymbol());
            String providerName = payload.getProviderName();

            log.debug("Ham kur işleniyor: symbol={}, provider={}, timestamp={}", 
                     symbolKey, providerName, payload.getTimestamp());

            // Pencereye kuru ekle/güncelle
            window.computeIfAbsent(symbolKey, k -> new ConcurrentHashMap<>())
                  .put(providerName, payload);

            // Tüm beklenen sağlayıcılardan veri geldi mi kontrol et
            checkWindowAndCalculate(symbolKey);
        } catch (ClassCastException e) {
            log.error("Payload RawRatePayloadDto'ya dönüştürülürken hata: {}", rateMessage.getPayload(), e);
        } catch (Exception e) {
            log.error("Kur mesajı işlenirken hata oluştu: {}", rateMessage, e);
        }
    }

    private void checkWindowAndCalculate(String symbolKey) {
        Map<String, RawRatePayloadDto> symbolBucket = window.get(symbolKey);
        List<String> expected = expectedProvidersPerSymbol.getOrDefault(symbolKey, Collections.emptyList());

        if (symbolBucket == null || expected.isEmpty()) {
            log.debug("Sembol için veri veya beklenen sağlayıcı yok: {}", symbolKey);
            return;
        }

        // Tüm beklenen sağlayıcılardan veri gelmiş mi kontrol et
        if (symbolBucket.keySet().containsAll(expected)) {
            log.debug("Sembol {} için tüm beklenen sağlayıcılar mevcut: beklenen={}, mevcut={}", 
                     symbolKey, expected, symbolBucket.keySet());

            // Zaman penceresi kontrolü
            long maxTs = symbolBucket.values().stream()
                                    .mapToLong(RawRatePayloadDto::getTimestamp)
                                    .max()
                                    .orElse(0);
            
            long minTs = symbolBucket.values().stream()
                                    .mapToLong(RawRatePayloadDto::getTimestamp)
                                    .min()
                                    .orElse(0);

            long timeDifference = maxTs - minTs;
            
            if (maxTs > 0 && minTs > 0 && timeDifference <= maxTimeSkew.toMillis()) {
                log.info("Sembol {} için tüm sağlayıcılardan zaman penceresi içinde ({}ms) veri geldi. Hesaplama tetikleniyor.", 
                        symbolKey, timeDifference);

                // Hesaplamayı tetikle
                rateCalculatorService.calculateAggregatedRate(symbolKey, symbolBucket);

                // Hesaplama sonrası bucket'ı temizle
                symbolBucket.clear();
            } else {
                log.warn("Sembol {} için tüm sağlayıcılardan veri geldi, fakat zaman penceresi dışında ({}ms > {}ms). Bucket temizleniyor.", 
                        symbolKey, timeDifference, maxTimeSkew.toMillis());
                
                // Zaman penceresi dışındaysa bucket'ı temizle
                symbolBucket.clear();
            }
        } else {
            log.debug("Sembol {} için bazı sağlayıcılardan hala veri bekleniyor: beklenen={}, mevcut={}", 
                     symbolKey, expected, symbolBucket.keySet());
        }
    }

    private String extractBaseSymbol(String providerSymbol) {
        if (providerSymbol == null) return "";
        
        // Sağlayıcı önekini kaldır (örn., "PF1_USDTRY" -> "USDTRY")
        int underscoreIndex = providerSymbol.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < providerSymbol.length() - 1) {
            return providerSymbol.substring(underscoreIndex + 1);
        }
        
        return providerSymbol;
    }
}
