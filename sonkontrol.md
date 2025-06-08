# Postgres'e bağlan
docker exec -it postgres psql -U postgres -d toyota_rates

# Tablo yapısını kontrol et
\d rates

# Migration geçmişini kontrol et
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

# Eğer sorun devam ederse veritabanını sıfırla:
docker-compose down
docker volume rm toyota_postgres-data
docker-compose up -d

-----------

# Tüm imajları listele
docker images

# Kullanılmayan imajları sil
docker image prune -a

# Belirli bir imajı sil
docker rmi [IMAGE_ID]

# Kullanılmayan tüm Docker kaynaklarını temizleme (dikkatli kullanın)
docker system prune -a

---------

# Kafka container'ına bağlan
docker exec -it kafka bash

# Topic'leri listeleme
kafka-topics --bootstrap-server localhost:9092 --list

docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic financial-simple-rates --from-beginning

docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic financial-raw-rates --from-beginning

docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic financial-calculated-rates --from-beginning

# Topic detaylarını görme
kafka-topics --bootstrap-server localhost:9092 --describe --topic financial-simple-rates

# Topic'teki mesajları okuma
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic financial-simple-rates \
  --from-beginning

# Yeni mesaj gönderme
kafka-console-producer --bootstrap-server localhost:9092 \
  --topic financial-simple-rates
> EURUSD_AVG|1.08393|1.08546|2025-06-08T18:54:20.197Z

-----

# Redis CLI'a bağlanma
docker exec -it redis redis-cli

# Tüm anahtarları listeleme
KEYS *

# Toyota önekli anahtarları listeleme
KEYS toyota*

# Belirli bir anahtarın değerini görme
GET toyota_rates:calc:EURUSD_AVG

# Anahtarları ve değerleri temizleme
DEL toyota_rates:calc:EURUSD_AVG
# Tüm anahtarları silme (dikkatli kullanın)
FLUSHALL

# Belirli bir pattern'e sahip anahtarları silme
redis-cli --scan --pattern "toyota_rates:*" | xargs redis-cli DEL

----

# Belirli bir servisin loglarını görüntüleme
docker logs kafka-consumer

# Logları canlı takip etme (-f follow)
docker logs -f main-application

# Son N satır log gösterme
docker logs --tail 100 main-application

# Belirli bir tarihten sonraki loglar
docker logs --since 2025-06-08T19:00:00 main-application

# Hata içeren logları filtreleme (grep ile)
docker logs main-application | grep ERROR

---

# PostgreSQL'e bağlanma
docker exec -it postgres psql -U postgres -d toyota_rates

# PostgreSQL CLI Komutları:
\dt                    # Tabloları listele
\d rates               # Rates tablosunun yapısını göster
\q                     # Çıkış yap

# SQL Sorguları:
SELECT COUNT(*) FROM rates;
SELECT * FROM rates ORDER BY rate_updatetime DESC LIMIT 10;
SELECT rate_name, COUNT(*) FROM rates GROUP BY rate_name;