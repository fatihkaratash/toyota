# Veri Sağlayıcılar (`rest-rate-provider` ve `tcp-rate-provider`) Kullanım Kılavuzu

Bu doküman, `rest-rate-provider` ve `tcp-rate-provider` servislerinin nasıl çalıştığını, hangi API'leri/protokolleri sunduğunu, nasıl veri ürettiklerini/simüle ettiklerini ve `main-application`'ın bu sağlayıcılara nasıl abone olduğunu açıklar. Bu kılavuz, [data-providers/monitoring-guide.md](data-providers/monitoring-guide.md) dosyasını tamamlayıcı niteliktedir.

## Genel Bakış

Veri sağlayıcı servisleri, `main-application` için ham (raw) döviz kuru verileri üretir. Projemizde iki ana sağlayıcı tipi bulunmaktadır:

1.  **`rest-rate-provider`:** REST API üzerinden kur verisi sunar.
2.  **`tcp-rate-provider`:** TCP soket bağlantısı üzerinden kur verisi akışı sağlar.

Her iki sağlayıcı da, gerçek zamanlı bir piyasayı simüle etmek için kur verilerinde dalgalanmalar (fluctuations) oluşturur. `main-application`, bu sağlayıcılara [`RestRateSubscriber`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/RestRateSubscriber.java) ve [`TcpRateSubscriber`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/TcpRateSubscriber.java) sınıfları aracılığıyla abone olur.

## `rest-rate-provider`

Bu servis, Spring Boot tabanlı bir uygulamadır ve RESTful endpoint'ler üzerinden döviz kuru verilerini JSON formatında sunar.

### API Endpoint'leri ve Veri Formatı

-   **Ana Endpoint:** `/api/rates/{symbol}`
    -   `{symbol}`: Talep edilen döviz çifti sembolü (örn: `PF2_USDTRY`, `PF2_EURUSD`). Sembol formatı için [docs/SymbolFormatStandard.md](docs/SymbolFormatStandard.md) dosyasına bakınız.
-   **HTTP Metodu:** `GET`
-   **Başarılı Yanıt (200 OK):**
    ```json
    {
      "symbol": "PF2_USDTRY", // Sağlayıcıya özgü sembol
      "bid": 32.50123,
      "ask": 32.50987,
      "timestamp": 1678886400000 // Epoch milisaniye
    }
    ```
    Bu yapı, `data-providers/rest-rate-provider/src/main/java/com/toyota/restserver/dto/ProviderRateDto.java` sınıfına karşılık gelir.
-   **Hata Yanıtları:**
    -   `404 Not Found`: Sembol bulunamazsa.
    -   `500 Internal Server Error`: Diğer sunucu taraflı hatalarda.

### Yapılandırma

-   **Port:** Varsayılan olarak `8080`. [docker-compose.yml](docker-compose.yml) dosyasında ve `data-providers/rest-rate-provider/src/main/resources/application.yml` dosyasında yapılandırılır.
-   **Simülasyon Parametreleri:** `application.yml` dosyasında `rate.simulation.*` anahtarları altında tanımlanır:
    -   `volatility`: Kur dalgalanmasının büyüklüğü.
    -   `minSpread`: Minimum alış-satış farkı.
    -   `maxRetries`: Geçerli bir kur üretmek için maksimum deneme sayısı.
-   **Loglama:** Log seviyeleri `application.yml` dosyasında ayarlanır. Loglar, [docker-compose.yml](docker-compose.yml) içinde tanımlanan volume mount (`./logs/rest-provider:/app/logs`) aracılığıyla ana makinedeki `logs/rest-provider` klasörüne yazılır.

### `main-application` ile Entegrasyon

-   [`RestRateSubscriber`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/RestRateSubscriber.java), `main-application` içinde bu sağlayıcıya bağlanır.
-   Bağlantı ve istekler, `WebClient` kullanılarak asenkron olarak yapılır.
-   Resilience4j ile Circuit Breaker ve Retry mekanizmaları entegre edilmiştir ([`RestRateSubscriber.fetchRate()`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/RestRateSubscriber.java) ve [`AppConfig.java`](main-application/src/main/java/com/toyota/mainapp/config/AppConfig.java)).
-   Abone olunacak semboller ve yoklama (polling) aralığı, `main-application`'daki `subscribers.json` (veya benzeri bir dinamik abone yapılandırma dosyası bkz: [`DynamicSubscriberLoader.java`](main-application/src/main/java/com/toyota/mainapp/subscriber/dynamic/DynamicSubscriberLoader.java)) üzerinden yapılandırılır.

## `tcp-rate-provider`

Bu servis, Java tabanlı bir TCP sunucusudur ve bağlı istemcilere sürekli olarak döviz kuru verisi akışı sağlar.

### TCP Protokolü ve Mesaj Formatı

-   **Bağlantı:** İstemciler, yapılandırılan host ve porta bir TCP soket bağlantısı açar.
-   **Abonelik:**
    -   İstemci, bağlandıktan sonra belirli kur sembollerine abone olmak için komut gönderir.
    -   Komut Formatı: `subscribe|{symbol}` (örn: `subscribe|PF1_USDTRY`)
    -   Semboller büyük/küçük harf duyarlı olabilir; istemci genellikle büyük harf gönderir ([`TcpRateSubscriber.connect()`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/TcpRateSubscriber.java)). Sunucu tarafında [`ClientHandler.processCommand()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/ClientHandler.java) bu komutları işler.
    -   Başarılı abonelik yanıtı: `Şuna abone olundu: {symbol}`
-   **Veri Akışı:**
    -   Abone olunan semboller için sunucu, periyodik olarak güncellenmiş kur verilerini gönderir.
    -   Mesaj Formatı: `{symbol}|{bid_price}|{ask_price}|{timestamp_epoch_millis}`
    -   Örnek: `PF1_USDTRY|32.49876|32.50765|1678886401000`
-   **Abonelik İptali:**
    -   Komut Formatı: `unsubscribe|{symbol}`
    -   Yanıt: `Şundan abonelik kaldırıldı: {symbol}`
-   **Hata Mesajları:** `ERROR|{hata_mesajı}`

### Yapılandırma

-   **Port:** Varsayılan olarak `8081`. [`ConfigurationReader.getServerPort()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/ConfigurationReader.java) metodu ile `tcp-provider.properties` dosyasından okunur. [docker-compose.yml](docker-compose.yml) dosyasında da port yönlendirmesi yapılır.
-   **Başlangıç Kurları (`initial-rates.json`):**
    -   Sunucu başladığında, simülasyon için temel alacağı başlangıç kurlarını `config/initial-rates.json` dosyasından yükler ([`ConfigurationReader.loadInitialRates()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/ConfigurationReader.java)). Bu dosya, Dockerfile aracılığıyla container içine kopyalanır.
    -   Dosya formatı:
      ```json
      [
        {"pairName": "PF1_USDTRY", "bid": 32.50, "ask": 32.51, "provider": "PF1"},
        {"pairName": "PF1_EURUSD", "bid": 1.0850, "ask": 1.0852, "provider": "PF1"}
      ]
      ```
-   **Simülasyon ve Yayın Parametreleri (`tcp-provider.properties`):**
    -   `publish.interval.ms`: Kurların ne sıklıkla yayınlanacağı ([`ConfigurationReader.getPublishIntervalMs()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/ConfigurationReader.java)).
    -   `fluctuation.volatility`: Kur dalgalanmasının büyüklüğü ([`ConfigurationReader.getFluctuationVolatility()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/ConfigurationReader.java)).
    -   `fluctuation.min.spread`: Minimum alış-satış farkı ([`ConfigurationReader.getMinSpread()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/ConfigurationReader.java)).
    -   `fluctuation.max.retries`: Geçerli bir kur üretmek için maksimum deneme sayısı ([`ConfigurationReader.getFluctuationMaxRetries()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/ConfigurationReader.java)).
-   **Loglama:** [`LoggingHelper.java`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/logging/LoggingHelper.java) sınıfı ve Log4j2 kullanılır. Loglar, [docker-compose.yml](docker-compose.yml) içinde tanımlanan volume mount (`./logs/tcp-provider:/app/logs`) aracılığıyla ana makinedeki `logs/tcp-provider` klasörüne yazılır.

### `main-application` ile Entegrasyon

-   [`TcpRateSubscriber`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/TcpRateSubscriber.java), `main-application` içinde bu sağlayıcıya bağlanır.
-   Bağlantı, standart Java soketleri (`java.net.Socket`) kullanılarak kurulur.
-   Bağlantı koptuğunda otomatik yeniden bağlanma mekanizması içerir ([`TcpRateSubscriber.reconnect()`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/TcpRateSubscriber.java)).
-   Abone olunacak semboller, `main-application`'daki `subscribers.json` (veya benzeri bir dinamik abone yapılandırma dosyası) üzerinden yapılandırılır.

## Veri Simülasyonu Mantığı

Her iki sağlayıcı da, başlangıç kurlarını aldıktan sonra bu kurları zamanla dalgalandırarak gerçekçi bir piyasa hareketi simüle eder.

-   **`rest-rate-provider`:** [`RateSimulationService.java`](data-providers/rest-rate-provider/src/main/java/com/toyota/restserver/service/RateSimulationService.java) sınıfı, her istek geldiğinde veya periyodik olarak kurları günceller.
-   **`tcp-rate-provider`:** [`RateFluctuationSimulator.java`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/RateFluctuationSimulator.java) sınıfı, [`RatePublisher.publishRates()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/RatePublisher.java) tarafından periyodik olarak çağrılarak kurları dalgalandırır.
-   **Dalgalanma Algoritması:** Genellikle mevcut kurun ortalamasını alır, buna rastgele bir volatilite faktörü ekler/çıkarır ve ardından minimum spread'i sağlayacak şekilde alış/satış fiyatlarını ayarlar.

## İzleme ve Hata Ayıklama

-   **Uygulama Logları:** Her iki sağlayıcının da kendi log dosyaları vardır (`logs/rest-provider` ve `logs/tcp-provider`). Bu loglar, bağlantı durumları, gönderilen/alınan veriler ve olası hatalar hakkında detaylı bilgi içerir.
    -   `rest-rate-provider` loglarında, gelen istekler, üretilen kurlar ve olası hatalar bulunur.
    -   `tcp-rate-provider` loglarında, istemci bağlantıları, abonelikler, yayınlanan kurlar ([`ClientHandler.sendRateUpdate()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/ClientHandler.java), [`RatePublisher.notifyListeners()`](data-providers/tcp-rate-provider/src/main/java/com/toyota/tcpserver/RatePublisher.java)) ve soket hataları bulunur.
-   **`main-application` Logları:**
    -   [`RestRateSubscriber`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/RestRateSubscriber.java) ve [`TcpRateSubscriber`](main-application/src/main/java/com/toyota/mainapp/subscriber/impl/TcpRateSubscriber.java) logları, sağlayıcılara bağlantı denemeleri, alınan veriler ve bağlantı hataları hakkında bilgi verir.
-   **Bağlantı Sorunları:**
    -   Docker network yapılandırmasının doğru olduğundan emin olun ([docker-compose.yml](docker-compose.yml)). `main-application`'ın `rest-rate-provider` ve `tcp-rate-provider` servislerine host adları (örn: `rest-rate-provider`, `tcp-rate-provider`) üzerinden erişebilmesi gerekir.
    -   Portların doğru şekilde map edildiğini ve çakışmadığını kontrol edin.
-   **Veri Akışı Kontrolü:**
    -   `tcp-rate-provider` için, bir `telnet` istemcisi veya basit bir TCP istemci programı ile bağlanıp `subscribe` komutu göndererek veri akışını manuel olarak test edebilirsiniz.
    -   `rest-rate-provider` için, bir tarayıcı veya `curl` gibi bir araçla API endpoint'lerine istek atarak yanıtları kontrol edebilirsiniz.

Bu kılavuz, veri sağlayıcı servislerinin temel işleyişini ve `main-application` ile etkileşimlerini anlamanıza yardımcı olmalıdır.