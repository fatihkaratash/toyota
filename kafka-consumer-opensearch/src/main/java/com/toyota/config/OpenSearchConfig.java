package com.toyota.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Toyota Financial Data Platform - OpenSearch Configuration
 * 
 * Configures OpenSearch client connection for financial rate data indexing.
 * Provides high-level REST client configuration for reliable connectivity
 * to the OpenSearch cluster within the Toyota platform infrastructure.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Configuration
public class OpenSearchConfig {

    @Value("${spring.elasticsearch.uris:http://opensearch:9200}")
    private String opensearchUri;

    @Bean
    public RestHighLevelClient opensearchClient() {
        // Parse the URI to get host and port
        String hostWithoutProtocol = opensearchUri.replace("http://", "");
        String host = hostWithoutProtocol.split(":")[0];
        int port = Integer.parseInt(hostWithoutProtocol.split(":")[1]);
        
        return new RestHighLevelClient(
            RestClient.builder(new HttpHost(host, port, "http"))
        );
    }
}