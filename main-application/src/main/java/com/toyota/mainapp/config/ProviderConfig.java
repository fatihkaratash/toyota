package com.toyota.mainapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@ConfigurationProperties(prefix = "providers")
public class ProviderConfig {

    private static final Logger logger = LoggerFactory.getLogger(ProviderConfig.class);

    private Rest rest = new Rest();
    private Tcp tcp = new Tcp();

    @PostConstruct
    public void logConfiguration() {
        logger.info("Provider Authentication Configuration Loaded:");
        logger.info("REST Provider - URL: '{}', Username: '{}', Password configured: {}", 
                   rest.url, rest.username, isPasswordConfigured(rest.password));
        logger.info("TCP Provider - Host: '{}', Port: {}, Username: '{}', Password configured: {}", 
                   tcp.host, tcp.port, tcp.username, isPasswordConfigured(tcp.password));
        
        if ("defaultuser".equals(rest.username) || "defaultpass".equals(rest.password)) {
            logger.warn("REST Provider is using default credentials! Please configure CLIENT_REST_USERNAME and CLIENT_REST_PASSWORD.");
        }
        if ("defaultuser".equals(tcp.username) || "defaultpass".equals(tcp.password)) {
            logger.warn("TCP Provider is using default credentials! Please configure CLIENT_TCP_USERNAME and CLIENT_TCP_PASSWORD.");
        }
    }

    private boolean isPasswordConfigured(String password) {
        return password != null && !password.isEmpty() && !"defaultpass".equals(password);
    }

    // Getters and Setters
    public Rest getRest() { return rest; }
    public void setRest(Rest rest) { this.rest = rest; }
    public Tcp getTcp() { return tcp; }
    public void setTcp(Tcp tcp) { this.tcp = tcp; }

    public static class Rest {
        private String url;
        private String username;
        private String password;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Tcp {
        private String host;
        private int port;
        private String username;
        private String password;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
