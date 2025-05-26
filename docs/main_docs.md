# `main-application` - Kur Hesaplama ve Toplama Motoru Kullanım Kılavuzu

Bu doküman, `main-application` servisinin temel işlevlerinden olan kur verisi toplama ([`TwoWayWindowAggregator`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java)) ve bu verilerden yeni kurlar hesaplama ([`RateCalculatorService`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java)) mekanizmalarının nasıl çalıştığını, nasıl yapılandırıldığını ve nasıl izlenebileceğini açıklar.

## Genel Bakış

`main-application`, farklı veri sağlayıcılardan (`rest-rate-provider`, `tcp-rate-provider`) gelen ham (raw) kur verilerini alır. Bu ham kurlar, belirli bir zaman penceresi içinde ve belirli sağlayıcı kombinasyonları için toplanır. Yeterli veri toplandığında, önceden tanımlanmış kurallara göre yeni kurlar (örneğin, ortalama kurlar, çapraz kurlar) hesaplanır.

**Ana Akış:**

1.  **Veri Alımı:** [`RestRateSubscriber`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/RestRateSubscriber.java) ve `TcpRateSubscriber` gibi aboneler, ham kur verilerini alır ve [`MainCoordinatorService`](main-application/src/main/java/com/toyota/mainapp/coordinator/MainCoordinatorService.java)'e iletir.
2.  **Ön İşleme:** [`MainCoordinatorService`](main-application/src/main/java/com/toyota/mainapp/coordinator/MainCoordinatorService.java), gelen ham kuru Redis'e önbellekler, Kafka'ya yayınlar ve [`TwoWayWindowAggregator`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java)'a gönderir.
3.  **Veri Toplama (Aggregation):** [`TwoWayWindowAggregator`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java), aynı temel sembole (örn: USDTRY) ait farklı sağlayıcılardan gelen ham kurları bir zaman penceresi içinde toplar.
4.  **Hesaplama Tetikleme:** Toplayıcı, belirli bir sembol için beklenen tüm sağlayıcılardan veri aldığında ve bu veriler arasındaki zaman farkı kabul edilebilir sınırlar içindeyse, [`RateCalculatorService`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java)'i tetikler.
5.  **Kur Hesaplama (Calculation):** [`RateCalculatorService`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java), `calculation-config.json` dosyasında tanımlanan kurallara ve stratejilere (örn: ortalama alma, Groovy script'leri) göre yeni kurlar hesaplar.
6.  **Sonuçların İşlenmesi:** Hesaplanan kurlar Redis'e önbelleklenir ve Kafka'ya yayınlanır.

## `TwoWayWindowAggregator` - Veri Toplayıcı

[`TwoWayWindowAggregator`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java), farklı sağlayıcılardan gelen ham kur verilerini temel sembol bazında gruplandırır ve bir zaman penceresi içinde tutar.

### Yapılandırma

-   **`expectedProvidersConfig`:** Hangi temel sembol için hangi sağlayıcılardan veri beklendiğini tanımlar. Bu yapılandırma, [`CalculationConfigLoader`](main-application/src/main/java/com/toyota/mainapp/config/CalculationConfigLoader.java) tarafından `calculation-config.json` dosyasındaki `symbolConfigs` bölümünden okunarak veya [`TwoWayWindowAggregator.initializeDefaultConfig()`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java) metodu ile varsayılan olarak ayarlanır.
    -   Örnek (`calculation-config.json`):
      ```json
      "symbolConfigs": [
        {
          "baseSymbol": "USDTRY",
          "expectedProviders": ["RESTProvider1", "TCPProvider2"]
        },
        // ... diğer semboller
      ]
      ```
-   **`maxTimeSkewMs`:** Aynı temel sembol için farklı sağlayıcılardan gelen kurlar arasındaki kabul edilebilir maksimum zaman farkı (milisaniye cinsinden). Bu değer `application.yml` dosyasından (`app.aggregator.max-time-skew-ms`) okunur. Varsayılan değeri 3000ms'dir.
-   **`windowCleanupIntervalMs`:** Eski veya tamamlanmamış pencere verilerinin ne sıklıkla temizleneceğini belirler (milisaniye cinsinden). Bu değer `application.yml` dosyasından (`app.aggregator.window-cleanup-interval-ms`) okunur. Varsayılan değeri 60000ms'dir. Temizleme işlemi [`cleanupStaleWindows()`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java) metodu ile periyodik olarak çalıştırılır.

### Çalışma Prensibi

1.  [`accept(BaseRateDto baseRateDto)`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java) metodu ile yeni bir ham kur (`RateType.RAW` olmalı) alır.
2.  Sağlayıcıya özgü sembolden (örn: `RESTProvider1_USDTRY`) temel sembolü (`USDTRY`) türetir ([`deriveBaseSymbol()`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java) ve [`SymbolUtils`](main-application/src/main/java/com/toyota/mainapp/util/SymbolUtils.java)).
3.  Kuru, ilgili temel sembolün penceresine (bucket) ekler. Pencere, `baseSymbol -> (providerName -> BaseRateDto)` şeklinde bir iç harita yapısında tutulur.
4.  [`checkWindowAndCalculate(String baseSymbol)`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java) metodunu çağırır:
    *   Bu sembol için beklenen tüm sağlayıcılardan ([`getExpectedProviders()`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java)) veri gelip gelmediğini kontrol eder.
    *   Gelen veriler arasındaki zaman farkının (`timestamp`) `maxTimeSkewMs` değerinden küçük olup olmadığını kontrol eder ([`isTimeSkewAcceptable()`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java)).
    *   Tüm koşullar sağlanırsa, toplanan ham kurların bir kopyasını ([`cloneRateDto()`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java)) alarak [`triggerCalculation()`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java) metodunu çağırır. Bu metot da [`RateCalculatorService.processWindowCompletion()`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java) metodunu tetikler.
    *   Hesaplama tetiklendikten sonra, kullanılan kurlar pencereden hemen silinmez; bunun yerine `lastCalculationTimestamp` alanları güncellenir. Bu, aynı ham kurların farklı hesaplamalarda tekrar kullanılabilmesine olanak tanır. Kurlar, periyodik `cleanupStaleWindows()` işlemi sırasında temizlenir.

## `RateCalculatorService` - Kur Hesaplama Servisi

[`RateCalculatorService`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java), toplayıcıdan gelen ham kur pencerelerini kullanarak yeni kurlar hesaplar.

### Yapılandırma

-   **`calculation-config.json`:** Hesaplama kurallarının tanımlandığı ana yapılandırma dosyasıdır. Bu dosya, [`CalculationConfigLoader`](main-application/src/main/java/com/toyota/mainapp/config/CalculationConfigLoader.java) tarafından uygulama başlangıcında yüklenir.
    -   **`calculationRules` bölümü:** Her bir hesaplama kuralını tanımlar. Bir kural şunları içerir:
        -   `ruleId`: Kural için benzersiz bir tanımlayıcı.
        -   `outputSymbol`: Hesaplama sonucu oluşacak kurun sembolü (örn: `USDTRY_AVG`, `EUR/TRY`).
        -   `strategyType`: Kullanılacak hesaplama stratejisinin adı (örn: `averageCalculationStrategy`, `groovyScriptCalculationStrategy`).
        -   `inputBaseSymbols`: Bu kuralın tetiklenmesi için hangi temel sembollerden ham kur verisi gerektiğini belirtir.
        -   `implementation`: Stratejiye özel ek yapılandırma. Örneğin, `groovyScriptCalculationStrategy` için çalıştırılacak Groovy script dosyasının yolu (`scripts/eur_try_calculator.groovy`).
        -   `parameters`: Stratejiye veya script'e geçirilecek ek parametreler (key-value çiftleri).
    -   Örnek Kural (`calculation-config.json` içinden):
      ```json
      {
        "ruleId": "EURTRY_CROSS_FROM_AVG",
        "outputSymbol": "EUR/TRY",
        "strategyType": "groovyScriptCalculationStrategy",
        "inputBaseSymbols": ["EURUSD", "USDTRY"], // Bu kuralın tetiklenmesi için hem EURUSD hem de USDTRY ham kurları gerekir
        "implementation": "scripts/eur_try_calculator.groovy",
        "parameters": {
          "eurUsdAvgKey": "calc_rate:EURUSD_AVG", // Script'in Redis'ten okuyacağı EURUSD ortalama kurunun anahtarı
          "usdTryAvgSourceKey": "calc_rate:USDTRY_AVG", // Script'in Redis'ten okuyacağı USDTRY ortalama kurunun anahtarı
          "defaultScale": "5"
        }
      }
      ```

### Çalışma Prensibi

1.  [`processWindowCompletion(Map<String, BaseRateDto> newlyCompletedRawRatesInWindow)`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java) metodu, [`TwoWayWindowAggregator`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java) tarafından tetiklenir. `newlyCompletedRawRatesInWindow` haritası, sağlayıcıya özgü sembollerle (örn: `RESTProvider1_USDTRY`) anahtarlanmış ham kurları içerir.
2.  Bu metot, gelen ham kurların temel sembollerini çıkarır.
3.  [`RuleEngineService`](main-application/src/main/java/com/toyota/mainapp/calculator/RuleEngineService.java) (bu servis `CalculationConfigLoader` tarafından yüklenen kuralları tutar) aracılığıyla, gelen ham kurların temel sembolleriyle eşleşen ve tetiklenebilecek tüm hesaplama kurallarını bulur.
4.  Tetiklenen her bir kural için [`calculateRateFromRule(CalculationRuleDto rule, Map<String, BaseRateDto> inputRatesForStrategy)`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java) metodunu çağırır.
5.  `calculateRateFromRule` metodu, kuralda tanımlanan `strategyType`'a göre uygun [`CalculationStrategy`](main-application/src/main/java/com/toyota/mainapp/calculator/engine/CalculationStrategy.java) implementasyonunu (Spring context üzerinden alınır) bulur ve `calculate()` metodunu çağırır.
    *   **`AverageCalculationStrategy`:** Girdilerden basit bir ortalama alır.
    *   **[`GroovyScriptCalculationStrategy`](main-application/src/main/java/com/toyota/mainapp/calculator/engine/impl/GroovyScriptCalculationStrategy.java):** Kuralda belirtilen Groovy script'ini (`implementation` alanındaki dosya yolu) çalıştırır.
        -   Script'e `inputRates` (ham kurlar), `rule.getParameters()` (kural parametreleri), `outputSymbol`, `log` (logger) ve `cache` ([`RateCacheService`](main-application/src/main/java/com/toyota/mainapp/cache/RateCacheService.java)) gibi değişkenler bağlanır.
        -   Script'ler ([`eur_try_calculator.groovy`](main-application/src/main/resources/scripts/eur_try_calculator.groovy), [`gbp_try_calculator.groovy`](main-application/src/main/resources/scripts/gbp_try_calculator.groovy)), genellikle Redis'ten daha önce hesaplanmış ortalama kurları (`_AVG` ile bitenler) okuyarak çapraz kurları hesaplar.
        -   Script, sonucu `bid` ve `ask` içeren bir `Map` olarak döndürmelidir.
        -   [`adaptInputRatesForScript()`](main-application/src/main/java/com/toyota/mainapp/calculator/engine/impl/GroovyScriptCalculationStrategy.java) metodu, Groovy script'lerine gönderilen `inputRates` haritasındaki anahtarların formatını (örn: `USDTRY_AVG` -> `USD/TRY_AVG`) uyarlayarak script'lerin daha esnek çalışmasını sağlar.
6.  Hesaplama başarılı olursa, sonuç `BaseRateDto` olarak oluşturulur, `RateType.CALCULATED` olarak işaretlenir, zaman damgası ve hesaplama girdileri eklenir.
7.  Bu hesaplanmış `BaseRateDto`, [`RateCacheService.cacheCalculatedRate()`](main-application/src/main/java/com/toyota/mainapp/cache/RateCacheService.java) ile Redis'e ve [`KafkaPublishingService.publishRate()`](main-application/src/main/java/com/toyota/mainapp/kafka/KafkaPublishingService.java) ile Kafka'ya gönderilir.

### Resilience4j Entegrasyonu

[`RateCalculatorService.processWindowCompletion()`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java) metodu, Resilience4j'in `@CircuitBreaker(name = "calculatorService")` ve `@Retry(name = "calculatorRetry")` anotasyonları ile işaretlenmiştir. Bu, hesaplama sırasında oluşabilecek geçici hatalara karşı dayanıklılığı artırır. İlgili yapılandırmalar [`AppConfig`](main-application/src/main/java/com/toyota/mainapp/config/AppConfig.java) sınıfında tanımlanır.

## Sembol Yönetimi

-   **Ham Kur Sembolleri:** Sağlayıcıdan geldiği gibi (örn: `PF1_USDTRY`). Formatı [docs/SymbolFormatStandard.md](docs/SymbolFormatStandard.md) dosyasında tanımlanmıştır.
-   **Temel Semboller:** Sağlayıcı öneki olmayan semboller (örn: `USDTRY`). [`SymbolUtils.deriveBaseSymbol()`](main-application/src/main/java/com/toyota/mainapp/util/SymbolUtils.java) ile türetilir.
-   **Hesaplanmış Kur Sembolleri:** `calculation-config.json` dosyasında `outputSymbol` olarak tanımlanır (örn: `USDTRY_AVG`, `EUR/TRY`). Formatı [docs/SymbolFormatStandard.md](docs/SymbolFormatStandard.md) dosyasında tanımlanmıştır.
-   **Redis Anahtarları:** Redis'e yazılırken özel önekler kullanılır (`raw_rate:`, `calc_rate:`). Detaylar için [docs/redis_kullanım.md](docs/redis_kullanım.md) dosyasına bakınız.

## İzleme ve Hata Ayıklama

-   **Loglar:**
    -   [`TwoWayWindowAggregator`](main-application/src/main/java/com/toyota/mainapp/aggregator/TwoWayWindowAggregator.java): Pencere durumu, beklenen sağlayıcılar, zaman kayması ve temizleme işlemleri hakkında detaylı loglar üretir.
    -   [`RateCalculatorService`](main-application/src/main/java/com/toyota/mainapp/calculator/RateCalculatorService.java): Tetiklenen kurallar, hesaplama adımları ve sonuçları hakkında loglar üretir.
    -   [`GroovyScriptCalculationStrategy`](main-application/src/main/java/com/toyota/mainapp/calculator/engine/impl/GroovyScriptCalculationStrategy.java): Yüklenen script'ler, script'e geçirilen değişkenler ve script sonucu hakkında loglar üretir. Groovy script'lerinin ([`eur_try_calculator.groovy`](main-application/src/main/resources/scripts/eur_try_calculator.groovy), [`gbp_try_calculator.groovy`](main-application/src/main/resources/scripts/gbp_try_calculator.groovy)) içine de `log.info(...)` gibi ifadeler eklenerek özel loglama yapılabilir.
-   **Redis:** [docs/redis_kullanım.md](docs/redis_kullanım.md) dosyasında açıklandığı gibi `redis-cli` ile önbellekteki ham ve hesaplanmış kurlar incelenebilir.
-   **Kafka:** [docs/kafka_kullanım.md](docs/kafka_kullanım.md) dosyasında açıklandığı gibi Kafka topic'leri dinlenerek yayınlanan ham ve hesaplanmış kurlar izlenebilir.

## Yeni Hesaplama Kuralları Ekleme

1.  Gerekirse yeni bir [`CalculationStrategy`](main-application/src/main/java/com/toyota/mainapp/calculator/engine/CalculationStrategy.java) implementasyonu oluşturun veya mevcut Groovy script'lerinden birini kopyalayıp düzenleyin.
2.  `calculation-config.json` dosyasına yeni bir kural tanımı ekleyin:
    *   `outputSymbol`'u belirleyin.
    *   Uygun `strategyType`'ı seçin.
    *   `inputBaseSymbols` listesini doğru şekilde tanımlayın.
    *   Eğer Groovy script kullanıyorsanız, `implementation` alanına script dosyasının yolunu (`scripts/` altından) ve `parameters` bölümüne gerekli parametreleri ekleyin.
3.  Uygulamayı yeniden başlattığınızda [`CalculationConfigLoader`](main-application/src/main/java/com/toyota/mainapp/config/CalculationConfigLoader.java) yeni kuralı yükleyecektir.