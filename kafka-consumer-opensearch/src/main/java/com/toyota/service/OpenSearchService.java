package main.java.com.toyota.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenSearchService {
    private final RestHighLevelClient opensearchClient;
    
    @Value("${app.opensearch.index-name}")
    private String indexName;
    
    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void logRateToOpensearch(String message, Acknowledgment acknowledgment) {
        try {
            log.debug("OpenSearch'e log gönderiliyor: {}", message);
            String[] parts = message.split("\\|");
            
            if (parts.length < 4) {
                log.warn("Geçersiz mesaj formatı: {}", message);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> document = new HashMap<>();
            document.put("rate_name", parts[0]);
            document.put("bid", parts[1]);
            document.put("ask", parts[2]);
            document.put("timestamp", parts[3]);
            document.put("received_at", System.currentTimeMillis());
            document.put("message", message);
            
            IndexRequest indexRequest = new IndexRequest(indexName)
                .id(UUID.randomUUID().toString())
                .source(document, XContentType.JSON);
            
            opensearchClient.index(indexRequest, RequestOptions.DEFAULT);
            log.info("Kur başarıyla OpenSearch'e loglandı: {}", parts[0]);
            
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("OpenSearch'e log gönderilirken hata: {}", message, e);
            // Sorunu çözmek için: yeniden deneme stratejisi veya DLQ
            acknowledgment.acknowledge(); // Şimdilik acknowledge et
        }
    }
}