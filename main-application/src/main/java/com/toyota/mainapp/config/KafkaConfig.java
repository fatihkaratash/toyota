// com.toyota.mainapp.config.KafkaConfig.java
package com.toyota.mainapp.config;

import com.fasterxml.jackson.databind.ObjectMapper; // ObjectMapper importu
import com.toyota.mainapp.dto.RateMessageDto; // DTO'nuzun yolu
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties; // Kaldırıldı, direkt producerConfigs kullanacağız
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${app.kafka.topic.raw-rates:financial-raw-rates}")
    private String rawRatesTopicName; // Değişken adı daha net

    @Value("${app.kafka.topic.calculated-rates:financial-calculated-rates}")
    private String calculatedRatesTopicName;

    @Value("${app.kafka.topic.simple-rates:financial-simple-rates}")
    private String simpleRatesTopicName;

    @Value("${app.kafka.topic.partitions:3}")
    private Integer topicPartitions;

    @Value("${app.kafka.topic.replication:1}")
    private Integer topicReplication;

    // ObjectMapper'ı enjekte edin (Spring Boot otomatik olarak bir tane sağlar)
    private final ObjectMapper objectMapper;

    public KafkaConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public Map<String, Object> baseProducerConfigs() { // baseConfigs olarak adlandıralım
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // VALUE_SERIALIZER_CLASS_CONFIG burada genel olarak ayarlanmaz, her factory kendi ayarlar.
        // Diğer ortak producer ayarları application.properties'den okunur (Spring Boot otomatik yapar)
        // props.put(ProducerConfig.ACKS_CONFIG, acks); // application.properties'den okunur
        // props.put(ProducerConfig.RETRIES_CONFIG, retries); // application.properties'den okunur
        log.info("Base Kafka producer properties configured for bootstrap servers: {}", bootstrapServers);
        return props;
    }

    // RateMessageDto için özel ProducerFactory
    @Bean
    public ProducerFactory<String, RateMessageDto> rateMessageProducerFactory() {
        Map<String, Object> configProps = new HashMap<>(baseProducerConfigs()); // Temel ayarları al
        // Değer serileştiricisini RateMessageDto için JsonSerializer olarak ayarla
        // ObjectMapper'ı JsonSerializer'a constructor ile ver
        JsonSerializer<RateMessageDto> valueSerializer = new JsonSerializer<>(objectMapper);
        // Güvenilen paketleri ekleyebilirsiniz, ancak genellikle ObjectMapper doğru yapılandırılmışsa gerekmez.
        // valueSerializer.setAddTypeInfo(false); // Tip bilgisini headera eklemeyi kapatmak için
        // valueSerializer.configure(Map.of(JsonSerializer.ADD_TYPE_INFO_HEADERS, false), false); // Alternatif
        
        DefaultKafkaProducerFactory<String, RateMessageDto> factory = new DefaultKafkaProducerFactory<>(configProps);
        factory.setValueSerializer(valueSerializer); // Serializer'ı set et
        return factory;
    }

    // RateMessageDto için özel KafkaTemplate
    @Bean
    public KafkaTemplate<String, RateMessageDto> rateMessageKafkaTemplate() {
        return new KafkaTemplate<>(rateMessageProducerFactory());
    }

    // String değerler için ProducerFactory
    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> configProps = new HashMap<>(baseProducerConfigs());
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    // String değerler için KafkaTemplate
    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    // Kafka Admin client for topic management (Bu kısım doğru görünüyor)
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic rawRatesTopic() {
        log.info("Creating Kafka topic: {}, partitions: {}, replication: {}",
                rawRatesTopicName, topicPartitions, topicReplication);
        return new NewTopic(rawRatesTopicName, topicPartitions, (short) topicReplication.intValue());
    }

    @Bean
    public NewTopic calculatedRatesTopic() {
        log.info("Creating Kafka topic: {}, partitions: {}, replication: {}",
                calculatedRatesTopicName, topicPartitions, topicReplication.intValue());
        return new NewTopic(calculatedRatesTopicName, topicPartitions, (short) topicReplication.intValue());
    }

    @Bean
    public NewTopic simpleRatesTopic() {
        log.info("Creating Kafka topic: {}, partitions: {}, replication: {}",
                simpleRatesTopicName, topicPartitions, topicReplication);
        return new NewTopic(simpleRatesTopicName, topicPartitions, (short) topicReplication.intValue());
    }
}