kafka-consumer Mikroservisi - Basitleştirilmiş Yol Haritası (Teknik Dokümana Göre)
📋 Proje Rolü:
kafka-consumer mikroservisi, main-application tarafından financial-simple-rates (veya benzeri, basit formatlı verilerin olduğu) Kafka topic'ine yayınlanan pipe-delimited string formatındaki kur verilerini tüketir. Bu verileri ayrıştırır ve PostgreSQL veritabanındaki TblRates tablosuna kaydeder. Ayrıca, bu işlenen verileri veya işlem loglarını OpenSearch'e gönderme işlevi de (ikincil olarak) eklenebilir.
🗂️ Önerilen Proje Yapısı (Sadeleştirilmiş):
kafka-consumer/
├── src/main/java/com/toyota/kafkaconsumer/
│   ├── KafkaconsumerApplication.java
│   ├── config/
│   │   └── KafkaConsumerConfig.java // Temel Kafka ayarları, StringDeserializer
│   ├── entity/
│   │   └── RateEntity.java          // TblRates için JPA Entity
│   ├── repository/
│   │   └── RateRepository.java      // Spring Data JPA
│   ├── listener/
│   │   └── SimpleRateListener.java  // Ana Kafka listener'ımız
│   ├── service/
│   │   └── PersistenceService.java  // DB'ye yazma mantığı
│   └── util/
│       └── RateParser.java          // Pipe-delimited string'i parse eder
├── src/main/resources/
│   ├── application.properties
│   └── log4j2.xml
├── Dockerfile
├── pom.xml
└── README.md
Use code with caution.
💡 Adım Adım Geliştirme Yol Haritası:
AŞAMA 1: Proje Kurulumu ve Temel Kafka Dinleyicisi
Yeni Spring Boot Projesi (kafka-consumer):
Bağımlılıklar: spring-kafka, spring-boot-starter-data-jpa, postgresql driver, lombok, log4j2. (OpenSearch bağımlılığını şimdilik eklemeyebiliriz, sonraki aşamaya bırakabiliriz).
application.properties Temel Ayarları:
Uygulama adı.
Kafka Bootstrap Sunucuları (kafka:29092).
Dinlenecek topic adı (app.kafka.topic.simple-rates=financial-simple-rates).
Consumer Group ID (app.kafka.consumer.group-id=simple-rate-db-persister-group).
Kafka Deserializer'ları:
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer (ÇOK ÖNEMLİ! Artık JSON değil, direkt String)

Expected Message Format:
- "TCPProvider1-USDTRY|34.52000|34.58000|2024-12-16T16:07:28.504Z"
- "USDTRY_AVG|34.50125|34.56875|2024-12-16T16:07:28.505Z"
- "GBPTRY_CROSS|42.15678|42.28934|2024-12-16T16:07:28.507Z"

spring.kafka.consumer.auto-offset-reset=earliest.
SimpleRateListener.java (İskelet):
@Service, @Slf4j.
@KafkaListener(topics = "${app.kafka.topic.simple-rates}", groupId = "${app.kafka.consumer.group-id}")
Metot: public void listenSimpleRates(String message, Acknowledgment ack);
Şimdilik sadece gelen message'ı loglasın: log.info("Received simple rate message: {}", message); ack.acknowledge();
AŞAMA 2: Veritabanı Katmanı ve Veri Ayrıştırma
RateEntity.java:
id (Long, @Id, @GeneratedValue(strategy = GenerationType.IDENTITY) - PostgreSQL SERIAL için).
rateName (String).
bid (BigDecimal).
ask (BigDecimal).
rateUpdatetime (LocalDateTime).
dbUpdatetime (LocalDateTime, @CreationTimestamp).
@Table(name = "tbl_rates").
RateRepository.java:
JpaRepository<RateEntity, Long>.
RateParser.java (Yardımcı Sınıf):
Statik metot: public static Optional<RateEntity> parseRateString(String rateString):
Gelen pipe-delimited string'i (SEMBOL|BID|ASK|TIMESTAMP) ayrıştırır.
TIMESTAMP'ı String'den LocalDateTime'a dönüştürür (DateTimeFormatter.ISO_LOCAL_DATE_TIME veya benzeri bir formatla).
BID ve ASK'ı String'den BigDecimal'e dönüştürür.
Bir RateEntity nesnesi oluşturup doldurur.
Ayrıştırma hatası olursa loglar ve Optional.empty() döner.
PersistenceService.java:
@Service, @Slf4j.
RateRepository'yi enjekte eder.
Metot: public void saveRate(RateEntity rateEntity) throws DataAccessException;
rateRepository.save(rateEntity) çağırır.
Loglama yapar.
SimpleRateListener.java Güncellemesi:
RateParser.parseRateString(message) ile string'i RateEntity'ye dönüştürür.
Eğer parse başarılıysa, PersistenceService.saveRate(entity) çağırır.
Başarılıysa ack.acknowledge(). Hata durumunda (parse hatası, DB hatası) loglar.
AŞAMA 3: OpenSearch Entegrasyonu (Loglama/İndeksleme Amacıyla - Daha Basit Tutulabilir)
Bu bölüm, teknik dökümandaki "bir diğeri ise opensearch / elasticsearch gibi bir platformu besleyerek loglama yapacaktır" ifadesine göre şekillenecek. Eğer amaç sadece işlem loglarını veya hatalı mesajları OpenSearch'e göndermekse, bu farklı bir yaklaşım gerektirir (örn: Log4j2'nin OpenSearch appender'ı veya Filebeat).
Eğer amaç, işlenen rate verilerini de OpenSearch'e indekslemekse (ki bu analiz için faydalı olabilir), o zaman:
Bağımlılık: spring-boot-starter-data-elasticsearch.
application.yml: OpenSearch URI'ları.
RateDocument.java (Basit):
Belki RateEntity'ye benzer alanlar içerir veya doğrudan RateEntity OpenSearch için de kullanılabilir (@Document anotasyonu ile). OpenSearch'e özel bir yapıya ihtiyaç varsa ayrı bir DTO daha iyi olur.
OpenSearchService.java (Basit):
OpenSearchRestTemplate (veya benzeri) enjekte edilir.
Metot: public void indexRateData(String indexName, Object rateData): Gelen veriyi (bu RateEntity veya basit bir Map olabilir) belirtilen indekse gönderir.
SimpleRateListener.java Güncellemesi:
PostgreSQL'e yazma başarılı olduktan sonra, OpenSearchService.indexRateData(...) çağrılır. Bu çağrı da kendi hata yönetimine sahip olmalı.
Gereksiz Olabilecek veya Sonraya Bırakılabilecekler (Önceki Kapsamlı Plandan):
Karmaşık RateMessageDto ve Payload DTO'ları: Artık Kafka mesajı basit string olduğu için bunlara gerek yok.
Ayrı CalculatedRateEntity, CalculatedRateListener vs.: Teknik döküman, Kafka'ya yazılan formatın USDTRY|34.20|... gibi hesaplanmış kurları da içerdiğini gösteriyor. Yani tek bir listener ve tek bir RateEntity ile tüm bu "basit formatlı" veriler işlenebilir. rateName alanı PF1_USDTRY veya USDTRY olabilir.
RawRateAggregatorListener gibi özel isimlendirmeler: Sadece SimpleRateListener yeterli.
Çok Detaylı Retry Policy Sınıfları: Başlangıç için Spring Retry'ın @Retryable anotasyonu servis metotlarında yeterli olabilir.
GlobalExceptionHandler (@ControllerAdvice): Bu daha çok REST API sunan uygulamalar için geçerlidir. Kafka listener'ları için Spring Kafka'nın kendi ErrorHandler mekanizmaları daha uygundur.