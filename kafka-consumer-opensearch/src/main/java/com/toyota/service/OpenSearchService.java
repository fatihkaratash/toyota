package com.toyota.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Toyota Financial Data Platform - OpenSearch Indexing Service
 * 
 * Kafka listener service that indexes financial rate data into OpenSearch.
 * Handles multiple data formats (pipe-delimited and JSON), processes different
 * rate types, and provides comprehensive indexing for analytics and monitoring.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OpenSearchService {
    
    private final RestHighLevelClient opensearchClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${app.opensearch.index-name.simple-rates}")
    private String simpleRatesIndexName;
    
    @Value("${app.opensearch.index-name.raw-rates}")
    private String rawRatesIndexName;
    
    @Value("${app.opensearch.index-name.calculated-rates}")
    private String calculatedRatesIndexName;
    
    @Value("${app.opensearch.index-name.pipeline-tracking}")
    private String pipelineTrackingIndexName;

    // ✅ Simple rates: PIPE-DELIMITED STRING format
    @KafkaListener(
        topics = "${app.kafka.topic.simple-rates}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void logSimpleRateToOpensearch(
            @Payload String message, 
            @Header(value = "kafka_receivedMessageKey", required = false) String pipelineId,
            Acknowledgment acknowledgment) {
        try {
            indexSimpleRateMessage(message, pipelineId, simpleRatesIndexName);
            acknowledgment.acknowledge();
            log.debug("Simple rate indexed with pipeline: {} [{}]", 
                     extractRateNameFromPipeString(message), pipelineId);
        } catch (Exception e) {
            log.error("Simple rate OpenSearch indexing error: {} [Pipeline: {}]", 
                     message, pipelineId, e);
            acknowledgment.acknowledge();
        }
    }

    // ✅ Raw rates: JSON format
    @KafkaListener(
        topics = "${app.kafka.topic.raw-rates}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void logRawRateToOpensearch(@Payload String message, Acknowledgment acknowledgment) {
        try {
            indexJsonMessage(message, rawRatesIndexName, "raw-rate");
            acknowledgment.acknowledge();
            log.debug("Raw rate indexed: {}", extractRateNameFromJson(message));
        } catch (Exception e) {
            log.error("Raw rate OpenSearch indexing error: {}", message, e);
            acknowledgment.acknowledge();
        }
    }

    // ✅ Calculated rates: JSON format
    @KafkaListener(
        topics = "${app.kafka.topic.calculated-rates}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void logCalculatedRateToOpensearch(@Payload String message, Acknowledgment acknowledgment) {
        try {
            indexJsonMessage(message, calculatedRatesIndexName, "calculated-rate");
            acknowledgment.acknowledge();
            log.debug("Calculated rate indexed: {}", extractRateNameFromJson(message));
        } catch (Exception e) {
            log.error("Calculated rate OpenSearch indexing error: {}", message, e);
            acknowledgment.acknowledge();
        }
    }

    // ✅ Pipeline tracking: PIPE-DELIMITED STRING format
    @KafkaListener(
        topics = "${app.kafka.topic.pipeline-tracking}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void logPipelineTrackingToOpensearch(
            @Payload String message,
            @Header(value = "kafka_receivedMessageKey", required = false) String pipelineId,
            Acknowledgment acknowledgment) {
        try {
            indexPipelineTrackingMessage(message, pipelineId, pipelineTrackingIndexName);
            acknowledgment.acknowledge();
            log.debug("Pipeline tracking indexed: {} [{}]", 
                     extractRateNameFromPipeString(message), pipelineId);
        } catch (Exception e) {
            log.error("Pipeline tracking OpenSearch indexing error: {} [Pipeline: {}]", 
                     message, pipelineId, e);
            acknowledgment.acknowledge();
        }
    }

    // ✅ Handle PIPE-DELIMITED format (for simple-rates)
    private void indexSimpleRateMessage(String message, String pipelineId, String indexName) throws Exception {
        String[] parts = message.split("\\|");
        
        if (parts.length < 4) {
            log.warn("Invalid simple rate message format (expected: SYMBOL|BID|ASK|TIMESTAMP): {}", message);
            return;
        }
        
        Map<String, Object> document = new HashMap<>();
        document.put("rate_name", parts[0]);
        document.put("bid", parts[1]);
        document.put("ask", parts[2]);
        document.put("timestamp", parts[3]);
        
        // Determine rate type from symbol
        String rateName = parts[0];
        String rateType = "RAW";
        if (rateName.contains("_AVG")) {
            rateType = "AVG";
        } else if (rateName.contains("_CROSS")) {
            rateType = "CROSS";
        }
        document.put("rate_type", rateType);
        document.put("pipeline_execution_id", pipelineId != null ? pipelineId : "unknown");
        document.put("received_at", System.currentTimeMillis());
        document.put("message", message);
        document.put("document_type", "simple-rate");
        
        IndexRequest indexRequest = new IndexRequest(indexName)
            .id(UUID.randomUUID().toString())
            .source(document, XContentType.JSON);
        
        opensearchClient.index(indexRequest, RequestOptions.DEFAULT);
    }

    // ✅ Handle JSON format (for raw-rates and calculated-rates)
    private void indexJsonMessage(String message, String indexName, String documentType) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            
            Map<String, Object> document = new HashMap<>();
            
            // Standard fields
            document.put("rate_name", getJsonField(jsonNode, "symbol"));
            document.put("bid", getJsonField(jsonNode, "bid"));
            document.put("ask", getJsonField(jsonNode, "ask"));
            document.put("timestamp", getJsonField(jsonNode, "timestamp"));
            document.put("provider_name", getJsonField(jsonNode, "providerName"));
            
            // Rate type determination
            String symbol = getJsonField(jsonNode, "symbol");
            String rateType = "RAW";
            if (symbol != null) {
                if (symbol.contains("_AVG")) {
                    rateType = "AVG";
                } else if (symbol.contains("_CROSS")) {
                    rateType = "CROSS";
                }
            }
            document.put("rate_type", rateType);
            
            // Additional JSON fields for calculated rates
            if ("calculated-rate".equals(documentType)) {
                document.put("calculation_type", getJsonField(jsonNode, "calculationType"));
                document.put("calculated_by_strategy", getJsonField(jsonNode, "calculatedByStrategy"));
                document.put("status", getJsonField(jsonNode, "status"));
                document.put("status_message", getJsonField(jsonNode, "statusMessage"));
                document.put("event_type", getJsonField(jsonNode, "eventType"));
                document.put("is_calculated_rate", getJsonField(jsonNode, "calculatedRate"));
                document.put("is_raw_rate", getJsonField(jsonNode, "rawRate"));
            }
            
            // Metadata
            document.put("received_at", System.currentTimeMillis());
            document.put("message", message);
            document.put("document_type", documentType);
            
            IndexRequest indexRequest = new IndexRequest(indexName)
                .id(UUID.randomUUID().toString())
                .source(document, XContentType.JSON);
            
            opensearchClient.index(indexRequest, RequestOptions.DEFAULT);
            
        } catch (Exception e) {
            log.error("Failed to parse and index JSON message to OpenSearch: {}", message, e);
            throw e;
        }
    }

    // ✅ Handle PIPE-DELIMITED format (for pipeline-tracking)
    private void indexPipelineTrackingMessage(String message, String pipelineId, String indexName) throws Exception {
        String[] parts = message.split("\\|");
        
        if (parts.length < 6) {
            log.warn("Invalid pipeline tracking message format (expected: SYMBOL|BID|ASK|TIMESTAMP|PIPELINE_ID|PROVIDER): {}", message);
            return;
        }
        
        Map<String, Object> document = new HashMap<>();
        document.put("rate_name", parts[0]);
        document.put("bid", parts[1]);
        document.put("ask", parts[2]);
        document.put("timestamp", parts[3]);
        document.put("pipeline_execution_id", parts[4]);
        document.put("provider_name", parts[5]);
        document.put("received_at", System.currentTimeMillis());
        document.put("message", message);
        document.put("document_type", "pipeline-tracking");
        
        // Rate type extraction
        String rateName = parts[0];
        String rateType = "RAW";
        if (rateName.contains("_AVG")) {
            rateType = "AVG";
        } else if (rateName.contains("_CROSS")) {
            rateType = "CROSS";
        }
        document.put("rate_type", rateType);
        
        IndexRequest indexRequest = new IndexRequest(indexName)
            .id(UUID.randomUUID().toString())
            .source(document, XContentType.JSON);
        
        opensearchClient.index(indexRequest, RequestOptions.DEFAULT);
    }

    // ✅ Helper methods
    private String getJsonField(JsonNode jsonNode, String fieldName) {
        return jsonNode.has(fieldName) ? jsonNode.get(fieldName).asText() : null;
    }

    private String extractRateNameFromPipeString(String message) {
        try {
            String[] parts = message.split("\\|");
            return parts.length > 0 ? parts[0] : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractRateNameFromJson(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            return getJsonField(jsonNode, "symbol");
        } catch (Exception e) {
            return "unknown";
        }
    }
}