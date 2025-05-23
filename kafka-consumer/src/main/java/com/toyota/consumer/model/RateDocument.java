package com.toyota.consumer.model; // veya document paketi

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
// import com.toyota.consumer.model.RateEntity; // Bu import gereksiz, fromEntity static metot
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.DateFormat; // Tarih formatı için

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(indexName = "rates_index" /* veya "simple_rates_index" */, createIndex = true) // createIndex=true geliştirme için
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateDocument {

    @Id // OpenSearch doküman ID'si için. Belki Kafka mesaj ID'si veya RateEntity.id olabilir.
    private String id; // String olması daha yaygın

    @Field(type = FieldType.Keyword)
    private String rateName; // "PF1_USDTRY", "USDTRY_AVG" gibi

    @Field(type = FieldType.Double) // OpenSearch'te ondalıklı sayılar için double veya float
    private BigDecimal bid;

    @Field(type = FieldType.Double)
    private BigDecimal ask;

    @Field(type = FieldType.Keyword) // Sağlayıcıyı veya kaynağı belirtmek için
    private String providerOrType; // Örn: "PF1", "PF2", "CALCULATED_AVG"

    // rateUpdatetime için ISO formatında saklamak ve OpenSearch'ün date olarak tanıması iyi olur
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime rateUpdatetime;

    // dbUpdatetime (PostgreSQL'e yazıldığı zaman) veya OpenSearch'e indekslendiği zaman
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime indexedAt;


    // Statik factory metodu RateEntity'den RateDocument oluşturmak için
    public static RateDocument fromEntity(RateEntity entity, String messageId) { // messageId Kafka'dan gelebilir
        if (entity == null) {
            return null;
        }

        String providerOrTypeInfo = entity.getRateName(); // Varsayılan olarak rateName
        // rateName'e göre providerOrType'ı daha anlamlı hale getirebiliriz (opsiyonel)
        if (entity.getRateName() != null) {
            if (entity.getRateName().startsWith("PF1_")) {
                providerOrTypeInfo = "PF1_RAW";
            } else if (entity.getRateName().startsWith("PF2_")) {
                providerOrTypeInfo = "PF2_RAW";
            } else if (entity.getRateName().contains("_AVG")) { // Veya sadece öneksizse "CALCULATED"
                providerOrTypeInfo = "CALCULATED_AVG";
            }
            // Diğer hesaplanmış kur türleri için de benzer mantık eklenebilir.
        }


        return RateDocument.builder()
            .id(messageId != null ? messageId : (entity.getId() != null ? entity.getId().toString() : null)) // Kafka mesaj ID'si varsa onu kullanmak daha iyi
            .rateName(entity.getRateName())
            .bid(entity.getBid())
            .ask(entity.getAsk())
            .providerOrType(providerOrTypeInfo)
            .rateUpdatetime(entity.getRateUpdatetime())
            .indexedAt(LocalDateTime.now()) // OpenSearch'e indekslendiği an
            .build();
    }
}