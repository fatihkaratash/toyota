package com.toyota.consumer.service;

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
public class LoggingConsumerService {

    private final RestHighLevelClient opensearchClient;

    @Value("${app.opensearch.index-name:financial_rates_logs}")
    private String indexName;

    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.logging-group-id:simple-rate-logging-group}"
    )
    public void logRateToOpensearch(String message, Acknowledgment acknowledgment) {
        log.debug("Received message for logging to OpenSearch: {}", message);
        
        try {
            // Parse the message
            String[] parts = message.split("\\|");
            if (parts.length < 4) {
                log.warn("Invalid message format for logging: {}", message);
                acknowledgment.acknowledge();
                return;
            }
            
            // Create document for OpenSearch
            Map<String, Object> document = new HashMap<>();
            document.put("rate_name", parts[0]);
            document.put("bid", parts[1]);
            document.put("ask", parts[2]);
            document.put("timestamp", parts[3]);
            document.put("received_at", System.currentTimeMillis());
            document.put("message", message);
            
            // Send to OpenSearch
            IndexRequest indexRequest = new IndexRequest(indexName)
                .id(UUID.randomUUID().toString())
                .source(document, XContentType.JSON);
                
            opensearchClient.index(indexRequest, RequestOptions.DEFAULT);
            log.info("Successfully logged rate to OpenSearch: {}", parts[0]);
            
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error logging rate to OpenSearch: {}", message, e);
            // Consider not acknowledging to retry, or implement DLQ
            acknowledgment.acknowledge(); // For now, acknowledge to prevent blocking
        }
    }
}