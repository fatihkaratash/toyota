package com.toyota.consumer.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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