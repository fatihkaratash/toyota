# PostgreSQL Veritabanı ve OpenSearch Kullanım Kılavuzu

Bu doküman, projemizde kullanılan PostgreSQL veritabanının ve OpenSearch arama/analitik motorunun rollerini, şemalarını, `kafka-consumer` servisinin bu sistemlerle nasıl etkileşimde bulunduğunu ve bu sistemlere nasıl erişilebileceğini açıklar.

## Genel Bakış

-   **PostgreSQL:** Ana kur verilerinin (hem ham hem de hesaplanmış) kalıcı olarak saklandığı ilişkisel veritabanıdır. `kafka-consumer` servisi, Kafka topic'lerinden okuduğu kur verilerini PostgreSQL'e yazar.
-   **OpenSearch:** Genellikle loglama, izleme ve analitik verilerin depolanması, aranması ve görselleştirilmesi için kullanılır. Projemizde, `filebeat` tarafından toplanan uygulama loglarının veya doğrudan `kafka-consumer` tarafından işlenen verilerin bir kopyasının OpenSearch'e gönderilmesi senaryoları olabilir.

Her iki servis de [docker-compose.yml](docker-compose.yml) dosyası aracılığıyla Docker container'ları olarak çalıştırılır.

## PostgreSQL Veritabanı

### Bağlantı Bilgileri

-   **Servis Adı (Docker Network):** `postgres`
-   **Port:** `5432` (Ana makineden erişim için `5432:5432` port yönlendirmesi [docker-compose.yml](docker-compose.yml) dosyasında tanımlıdır)
-   **Veritabanı Adı:** `toyota_rates`
-   **Kullanıcı Adı:** `postgres`
-   **Şifre:** `pgadmin` (Bu değerler `.env` dosyasından veya [docker-compose.yml](docker-compose.yml) içindeki `environment` bölümünden gelir)
-   **JDBC URL (`kafka-consumer` için):** `jdbc:postgresql://postgres:5432/toyota_rates` (bkz: [`kafka-consumer/src/main/resources/application.yml`](kafka-consumer/src/main/resources/application.yml))

### Veritabanı Şeması ve Ana Tablolar

Veritabanı şeması, [`postgres-init-scripts/init.sql`](postgres-init-scripts/init.sql) (veya benzeri bir dosya) içinde tanımlanır. Bu script, veritabanı container'ı ilk kez başlatıldığında otomatik olarak çalıştırılır.

**Örnek Ana Tablolar (Tahmini):**

-   **`financial_rates` (veya benzeri bir isim):**
    -   `id` (PRIMARY KEY, auto-increment)
    -   `symbol` (VARCHAR): Kur sembolü (örn: `USDTRY_AVG`, `PF1_EURUSD`)
    -   `rate_type` (VARCHAR): Kurun tipi (`RAW` veya `CALCULATED`)
    -   `provider_name` (VARCHAR, nullable): Ham kurlar için sağlayıcı adı.
    -   `bid_price` (DECIMAL): Alış fiyatı.
    -   `ask_price` (DECIMAL): Satış fiyatı.
    -   `rate_timestamp` (TIMESTAMP): Kurun zaman damgası.
    -   `created_at` (TIMESTAMP, default NOW()): Kaydın oluşturulma zamanı.
    -   `source_topic` (VARCHAR, nullable): Verinin geldiği Kafka topic'i.

Bu tablo yapısı, `kafka-consumer` modülündeki JPA Entity sınıflarına ([`RateLog.java`](kafka-consumer/src/main/java/com/toyota/consumer/model/RateLog.java) gibi) karşılık gelir.

### `kafka-consumer`'ın Veri Yazma Mantığı

-   **Dinlenen Kafka Topic'leri:**
    -   `financial-simple-rates`: [`SimpleRateListener`](kafka-consumer/src/main/java/com/toyota/consumer/listener/SimpleRateListener.java) tarafından dinlenir. Bu listener, metin formatındaki mesajları ayrıştırır ve veritabanına kaydeder.
    -   `financial-raw-rates` ve `financial-calculated-rates` (JSON formatlı): Bu topic'ler için de `kafka-consumer` içinde benzer listener'lar olabilir. Bu listener'lar JSON mesajlarını [`RateMessageDto`](main-application/src/main/java/com/toyota/mainapp/dto/kafka/RateMessageDto.java) ve [`RatePayloadDto`](main-application/src/main/java/com/toyota/mainapp/dto/kafka/RatePayloadDto.java) gibi DTO'lara dönüştürüp, ardından JPA Entity'lerine map ederek veritabanına kaydeder.
-   **Veri Kaydı:**
    -   [`RateLogService`](kafka-consumer/src/main/java/com/toyota/consumer/service/RateLogService.java) ve [`RateLogRepository`](kafka-consumer/src/main/java/com/toyota/consumer/repository/RateLogRepository.java) (Spring Data JPA) aracılığıyla veriler ilgili tablolara yazılır.

### Veritabanına Erişim ve Sorgulama

-   **`psql` Komut Satırı Aracı:**
    ```sh
    docker exec -it postgres psql -U postgres -d toyota_rates
    ```
    İçerideyken SQL komutları çalıştırılabilir:
    ```sql
    SELECT * FROM financial_rates ORDER BY rate_timestamp DESC LIMIT 10;
    SELECT COUNT(*) FROM financial_rates WHERE symbol = 'USDTRY_AVG';
    ```
-   **GUI Araçları:** DBeaver, pgAdmin gibi veritabanı yönetim araçları, ana makineden `localhost:5432` adresine bağlanarak kullanılabilir.

## OpenSearch

OpenSearch, genellikle loglama ve gerçek zamanlı analitik için kullanılır.

### Bağlantı Bilgileri

-   **Servis Adı (Docker Network):** `opensearch`
-   **Port:** `9200` (Ana makineden erişim için `9200:9200` port yönlendirmesi [docker-compose.yml](docker-compose.yml) dosyasında tanımlıdır)
-   **OpenSearch Dashboards:** `http://localhost:5601` (OpenSearch verilerini görselleştirmek ve keşfetmek için arayüz)
-   **Bağlantı URI (`kafka-consumer` için):** `http://opensearch:9200` (bkz: [`OpenSearchConfig.java`](kafka-consumer/src/main/java/com/toyota/consumer/config/OpenSearchConfig.java) ve [`kafka-consumer/src/main/resources/application.yml`](kafka-consumer/src/main/resources/application.yml))

### Veri Akışı ve Index'ler

-   **Filebeat ile Log Toplama:**
    -   [filebeat/filebeat.yml](filebeat/filebeat.yml) dosyasında yapılandırıldığı üzere, `filebeat` servisi diğer container'ların log dosyalarını (örneğin, `main-application`, `rest-rate-provider` logları) izler.
    -   Bu loglar işlenir ve OpenSearch'e gönderilir. Genellikle günlük bazlı index'lere (örn: `filebeat-YYYY.MM.DD`) yazılır.
-   **`kafka-consumer` ile Doğrudan Veri Yazma (Opsiyonel):**
    -   `kafka-consumer` servisi, işlediği kur verilerinin bir kopyasını veya bu verilerden türetilmiş analitik bilgileri doğrudan OpenSearch'e yazabilir.
    -   Bu durumda, [`OpenSearchService.java`](kafka-consumer/src/main/java/com/toyota/consumer/service/OpenSearchService.java) gibi bir servis ve özel bir OpenSearch index'i (örn: `financial_rates_analytics`) kullanılabilir. [`kafka-consumer/src/main/resources/application.yml`](kafka-consumer/src/main/resources/application.yml) dosyasındaki `app.opensearch.index-name` bu index adını belirleyebilir.

### OpenSearch'e Erişim ve Sorgulama

-   **OpenSearch Dashboards:**
    -   `http://localhost:5601` adresinden erişilir.
    -   "Discover" sekmesi üzerinden index'lerdeki veriler keşfedilebilir, filtrelenebilir ve sorgulanabilir (Lucene sorgu sözdizimi veya DQL).
    -   "Visualize" ve "Dashboard" sekmeleri ile verilerden grafikler ve gösterge panoları oluşturulabilir.
-   **REST API:**
    -   `curl` veya Postman gibi araçlarla OpenSearch REST API'sine istekler gönderilebilir.
    -   **Index'leri Listeleme:**
        ```sh
        curl -X GET "http://localhost:9200/_cat/indices?v"
        ```
    -   **Bir Index'teki Verileri Sorgulama (Örnek):**
        ```sh
        curl -X GET "http://localhost:9200/financial_rates_logs/_search?pretty" -H 'Content-Type: application/json' -d'
        {
          "query": {
            "match": {
              "message": "USDTRY"
            }
          },
          "sort": [
            { "@timestamp": "desc" }
          ],
          "size": 5
        }
        '
        ```

## Bakım ve İzleme

-   **PostgreSQL:**
    -   Düzenli yedekleme stratejileri düşünülmelidir (üretim ortamları için).
    -   Veritabanı logları (`docker logs postgres` veya container içindeki log dosyaları) bağlantı ve sorgu hataları için izlenmelidir.
-   **OpenSearch:**
    -   Cluster sağlığı (`curl -X GET "http://localhost:9200/_cluster/health?pretty"`), disk alanı ve shard durumları izlenmelidir.
    -   OpenSearch logları (`docker logs opensearch`) hatalar için kontrol edilmelidir.
    -   Index lifecycle management (ILM) politikaları, eski verilerin otomatik olarak yönetilmesi (arşivleme, silme) için yapılandırılabilir.

Bu kılavuz, PostgreSQL ve OpenSearch'in projenizdeki temel kullanımını ve bu sistemlerle nasıl etkileşimde bulunulacağını anlamanıza yardımcı olmalıdır.