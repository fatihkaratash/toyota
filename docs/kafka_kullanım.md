# Kafka Kullanım Kılavuzu

Bu doküman, projemizdeki Apache Kafka'nın nasıl kullanıldığını, hangi servislerin veri ürettiğini (producer) ve tükettiğini (consumer), kullanılan topic'leri, mesaj formatlarını ve Kafka'nın nasıl izlenebileceğini açıklar.

## Genel Bakış

Apache Kafka, projemizde servisler arası asenkron mesajlaşma ve veri akışlarını yönetmek için kullanılır. Özellikle `main-application` tarafından işlenen ham ve hesaplanmış kur verilerinin, `kafka-consumer` gibi diğer servislere güvenilir bir şekilde iletilmesini sağlar. Bu, sistem bileşenlerinin birbirinden bağımsız (decoupled) çalışmasına olanak tanır.

Kafka, [docker-compose.yml](docker-compose.yml) dosyası aracılığıyla Zookeeper ile birlikte bir Docker container'ı olarak çalıştırılır.

## Kafka Üreticileri (Producers)

Ana Kafka üreticisi `main-application` servisidir.

-   **Sorumlu Sınıf:** [`main-application/src/main/java/com/toyota/mainapp/kafka/KafkaPublishingService.java`](main-application/src/main/java/com/toyota/mainapp/kafka/KafkaPublishingService.java)
    -   Bu servis, hem ham (raw) hem de hesaplanmış (calculated) kur verilerini uygun Kafka topic'lerine yayınlar.
    -   Farklı veri türleri için farklı topic'ler ve mesaj formatları kullanılır.

## Kafka Tüketicileri (Consumers)

Ana Kafka tüketicisi `kafka-consumer` servisidir.

-   **Sorumlu Sınıflar (Örnek):**
    -   [`kafka-consumer/src/main/java/com/toyota/consumer/listener/SimpleRateListener.java`](kafka-consumer/src/main/java/com/toyota/consumer/listener/SimpleRateListener.java): `financial-simple-rates` topic'indeki basitleştirilmiş metin formatındaki kur mesajlarını dinler ve veritabanına kaydeder.
    -   (Projenin `kafka-consumer` modülünde, `financial-raw-rates` ve `financial-calculated-rates` gibi JSON tabanlı topic'leri dinleyen başka listener'lar da olabilir veya eklenebilir.)

## Kafka Topic'leri ve Mesaj Formatları

Projede kullanılan ana Kafka topic'leri ve içerikleri aşağıdaki gibidir. Topic isimleri ve yapılandırmaları `main-application` içindeki [`KafkaConfig.java`](main-application/src/main/java/com/toyota/mainapp/config/KafkaConfig.java) ve ilgili `application.yml` dosyalarında tanımlanır.

### 1. `financial-raw-rates`

-   **Amaç:** Veri sağlayıcılardan (`rest-rate-provider`, `tcp-rate-provider`) gelen ham (işlenmemiş) kur verilerini taşır.
-   **Üretici:** `main-application` ([`KafkaPublishingService`](main-application/src/main/java/com/toyota/mainapp/kafka/KafkaPublishingService.java))
-   **Mesaj Formatı:** JSON
    -   Mesaj yapısı: [`RateMessageDto`](main-application/src/main/java/com/toyota/mainapp/dto/kafka/RateMessageDto.java)
    -   Payload (`RateMessageDto` içindeki `payload` alanı): [`RatePayloadDto`](main-application/src/main/java/com/toyota/mainapp/dto/kafka/RatePayloadDto.java) (Bu, `BaseRateDto`'dan dönüştürülür ve `providerName`, `symbol`, `bid`, `ask`, `timestamp` gibi detayları içerir).
-   **Örnek Tüketici:** Bu topic için `kafka-consumer` modülünde özel bir listener olabilir (mevcut dosya kesitlerinde doğrudan görünmemektedir, ancak [`kafka-consumer/src/main/resources/application.yml`](kafka-consumer/src/main/resources/application.yml) dosyasında `app.kafka.topic.json-rates` gibi bir yapılandırma bulunabilir).

### 2. `financial-calculated-rates`

-   **Amaç:** `main-application` tarafından ham kurlar kullanılarak hesaplanmış (örneğin, ortalama, çapraz kur) kur verilerini taşır.
-   **Üretici:** `main-application` ([`KafkaPublishingService`](main-application/src/main/java/com/toyota/mainapp/kafka/KafkaPublishingService.java))
-   **Mesaj Formatı:** JSON
    -   Mesaj yapısı: [`RateMessageDto`](main-application/src/main/java/com/toyota/mainapp/dto/kafka/RateMessageDto.java)
    -   Payload: [`RatePayloadDto`](main-application/src/main/java/com/toyota/mainapp/dto/kafka/RatePayloadDto.java) (Hesaplanmış `BaseRateDto`'dan dönüştürülür).
-   **Örnek Tüketici:** `financial-raw-rates` topic'ine benzer şekilde, bu topic için de `kafka-consumer` modülünde özel bir listener olabilir.

### 3. `financial-simple-rates`

-   **Amaç:** Seçilmiş ham ve hesaplanmış kurların basitleştirilmiş bir metin formatında taşınması. Bu topic genellikle `kafka-consumer` tarafından veritabanına hızlı kayıt veya basit loglama/izleme için kullanılır.
-   **Üretici:** `main-application` ([`KafkaPublishingService`](main-application/src/main/java/com/toyota/mainapp/kafka/KafkaPublishingService.java))
-   **Mesaj Formatı:** Plain String
    -   Format: `SYMBOL|BID_PRICE|ASK_PRICE|TIMESTAMP`
    -   Örnek: `USDTRY_AVG|32.5012|32.5098|2023-10-27T10:30:00.123`
-   **Tüketici:** `kafka-consumer` ([`SimpleRateListener`](kafka-consumer/src/main/java/com/toyota/consumer/listener/SimpleRateListener.java))

## Yapılandırma Dosyaları

-   **`main-application`:**
    -   [`main-application/src/main/java/com/toyota/mainapp/config/KafkaConfig.java`](main-application/src/main/java/com/toyota/mainapp/config/KafkaConfig.java): Kafka topic'lerinin programatik olarak oluşturulması (bean tanımları) ve Kafka producer factory'lerinin yapılandırılması.
    -   `main-application/src/main/resources/application.yml` (veya `.properties`): Kafka bootstrap sunucuları, varsayılan topic isimleri gibi temel bağlantı ve topic ayarları.
-   **`kafka-consumer`:**
    -   [`kafka-consumer/src/main/java/com/toyota/consumer/config/KafkaConsumerConfig.java`](kafka-consumer/src/main/java/com/toyota/consumer/config/KafkaConsumerConfig.java): Kafka consumer factory ve listener container factory'lerinin yapılandırılması (örneğin, manuel onaylama modu, paralellik).
    -   [`kafka-consumer/src/main/resources/application.yml`](kafka-consumer/src/main/resources/application.yml): Kafka bootstrap sunucuları, dinlenecek topic isimleri, consumer group ID'leri, deserializer ayarları.

## Kafka'yı İzleme ve Hata Ayıklama

Kafka cluster'ının ve mesaj akışlarının durumunu izlemek için çeşitli yöntemler kullanılabilir:

1.  **Kafka Komut Satırı Araçları:**
    Kafka dağıtımıyla birlikte gelen araçlar, Docker container'ı içinden çalıştırılabilir (`docker exec -it kafka bash` komutuyla container'a girdikten sonra):
    -   **Topic'leri Listeleme:**
        ```sh
        kafka-topics --bootstrap-server localhost:29092 --list
        ```
    -   **Bir Topic'i Dinleme (Mesajları Görme):**
        ```sh
        # JSON topic (financial-raw-rates veya financial-calculated-rates)
        kafka-console-consumer --bootstrap-server localhost:29092 --topic financial-raw-rates --from-beginning

        # String topic (financial-simple-rates)
        kafka-console-consumer --bootstrap-server localhost:29092 --topic financial-simple-rates --from-beginning
        ```
    -   **Topic Detaylarını Görme:**
        ```sh
        kafka-topics --bootstrap-server localhost:29092 --describe --topic financial-raw-rates
        ```
    -   **Consumer Gruplarını Listeleme:**
        ```sh
        kafka-consumer-groups --bootstrap-server localhost:29092 --list
        ```
    -   **Bir Consumer Grubunun Durumunu Görme (Lag Kontrolü):**
        ```sh
        kafka-consumer-groups --bootstrap-server localhost:29092 --describe --group <consumer-group-id>
        ```
        (`<consumer-group-id>` yerine [`kafka-consumer/src/main/resources/application.yml`](kafka-consumer/src/main/resources/application.yml) dosyasındaki grup ID'sini yazın, örn: `simple-rate-persister-group`)

2.  **Uygulama Logları:**
    -   **`main-application` Logları:** [`KafkaPublishingService`](main-application/src/main/java/com/toyota/mainapp/kafka/KafkaPublishingService.java) logları, mesajların başarıyla gönderilip gönderilmediği hakkında bilgi verir.
    -   **`kafka-consumer` Logları:** [`SimpleRateListener`](kafka-consumer/src/main/java/com/toyota/consumer/listener/SimpleRateListener.java) ve diğer listener sınıflarının logları, mesajların alınıp işlenmesiyle ilgili detayları ve olası hataları gösterir.

3.  **Harici İzleme Araçları (Opsiyonel):**
    Daha kapsamlı izleme için Confluent Control Center, Prometheus/Grafana ile JMX metrikleri, Datadog gibi araçlar entegre edilebilir.

## Önemli Sınıflar ve Dosyalar

-   **Üretici Tarafı (`main-application`):**
    -   [`KafkaPublishingService.java`](main-application/src/main/java/com/toyota/mainapp/kafka/KafkaPublishingService.java): Mesaj yayınlama mantığı.
    -   [`KafkaConfig.java`](main-application/src/main/java/com/toyota/mainapp/config/KafkaConfig.java): Producer ve topic yapılandırması.
    -   [`RateMessageDto.java`](main-application/src/main/java/com/toyota/mainapp/dto/kafka/RateMessageDto.java): JSON mesajları için ana DTO.
    -   [`RatePayloadDto.java`](main-application/src/main/java/com/toyota/mainapp/dto/kafka/RatePayloadDto.java): JSON mesaj payload'u için DTO.
-   **Tüketici Tarafı (`kafka-consumer`):**
    -   [`SimpleRateListener.java`](kafka-consumer/src/main/java/com/toyota/consumer/listener/SimpleRateListener.java): `financial-simple-rates` topic'i için listener.
    -   [`KafkaConsumerConfig.java`](kafka-consumer/src/main/java/com/toyota/consumer/config/KafkaConsumerConfig.java): Consumer yapılandırması.
    -   [`application.yml`](kafka-consumer/src/main/resources/application.yml): Consumer'a özel topic ve grup ID ayarları.
-   **Genel:**
    -   [docker-compose.yml](docker-compose.yml): Kafka ve Zookeeper servislerinin tanımı.

## Sonuç

Kafka, projemizin veri akış mimarisinde merkezi bir rol oynar. Servisler arasında esnek, ölçeklenebilir ve dayanıklı bir mesajlaşma altyapısı sunar. Yukarıdaki bilgiler, sistemdeki Kafka kullanımını anlamak, izlemek ve geliştirmek için bir temel oluşturur.