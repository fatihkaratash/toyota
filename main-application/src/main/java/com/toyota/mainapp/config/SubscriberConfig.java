package com.toyota.mainapp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Veri sağlayıcı abonelerinin yapılandırmasını içeren sınıf
 */
@Configuration
@Getter
public class SubscriberConfig {

    @Value("${app.provider.tcp.default-port:8081}")
    private int defaultTcpPort;

    @Value("${app.provider.rest.default-port:8080}")
    private int defaultRestPort;

    @Value("${app.provider.tcp.default-host:localhost}")
    private String defaultTcpHost;

    @Value("${app.provider.rest.default-host:localhost}")
    private String defaultRestHost;
}
