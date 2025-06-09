package com.toyota.tcpserver.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyota.tcpserver.logging.LoggingHelper;
import com.toyota.tcpserver.model.Rate; // Add this import

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Toyota Financial Data Platform - TCP Configuration Reader
 * 
 * Configuration management for TCP rate provider with properties loading,
 * initial rate data loading, and security credential management. Supports
 * both classpath and filesystem configuration sources.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
public class ConfigurationReader {
    private static final LoggingHelper log = new LoggingHelper(ConfigurationReader.class);
    private static final String PROPERTIES_FILE = "tcp-provider.properties";
    private static final String INITIAL_RATES_JSON_CLASSPATH = "initial-rates.json"; // Sınıf yolundan (src/main/resources/config)
    private static final String INITIAL_RATES_JSON_FILESYSTEM = "config/initial-rates.json"; // Dosya sisteminden (./config)


    private final Properties properties;
    private List<Rate> initialRates;

    public ConfigurationReader() {
        properties = new Properties();
        loadProperties();
        loadInitialRates();
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                log.error(LoggingHelper.PLATFORM_TCP, null, 
                        "Üzgünüz, " + PROPERTIES_FILE + " bulunamadı");
                throw new RuntimeException("Yapılandırma dosyası " + PROPERTIES_FILE + " sınıf yolunda bulunamadı.");
            }
            properties.load(input);
            log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, 
                    PROPERTIES_FILE + " dosyasından yapılandırma başarıyla yüklendi");
        } catch (IOException ex) {
            log.error(LoggingHelper.PLATFORM_TCP, null, 
                    PROPERTIES_FILE + " dosyasından yapılandırma yüklenirken hata", ex);
            throw new RuntimeException(PROPERTIES_FILE + " dosyasından yapılandırma yüklenirken hata", ex);
        }
    }

    private void loadInitialRates() {
        ObjectMapper mapper = new ObjectMapper();
        InputStream jsonInput = null;
        try {
            if (Files.exists(Paths.get(INITIAL_RATES_JSON_FILESYSTEM))) {
                jsonInput = Files.newInputStream(Paths.get(INITIAL_RATES_JSON_FILESYSTEM));
                log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, null, 
                        "Başlangıç kurları dosya sisteminden yükleniyor: " + INITIAL_RATES_JSON_FILESYSTEM);
            }
            else {
                 jsonInput = getClass().getClassLoader().getResourceAsStream(INITIAL_RATES_JSON_CLASSPATH);
                 if (jsonInput == null) { // sınıf yolu için "config/" önekiyle dene
                     jsonInput = getClass().getClassLoader().getResourceAsStream("config/" + INITIAL_RATES_JSON_CLASSPATH);
                 }

                if (jsonInput == null) {
                    log.error(LoggingHelper.PLATFORM_PF1, null, 
                            "Üzgünüz, " + INITIAL_RATES_JSON_CLASSPATH + " sınıf yolunda veya dosya sisteminde bulunamadı.");
                    throw new FileNotFoundException(INITIAL_RATES_JSON_CLASSPATH + " bulunamadı.");
                }
                log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, null, 
                        "Başlangıç kurları sınıf yolundan yükleniyor: " + INITIAL_RATES_JSON_CLASSPATH);
            }

            initialRates = mapper.readValue(jsonInput, new TypeReference<List<Rate>>() {});
            initialRates.forEach(rate -> {
                if (rate.getTimestamp() == null || rate.getTimestamp().isEmpty()) {
                    rate.setCurrentTimestamp();
                }
            });
            log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, null, 
                    "Başarıyla " + initialRates.size() + " başlangıç kuru yüklendi.");

        } catch (IOException ex) {
            log.error(LoggingHelper.PLATFORM_PF1, null, 
                    INITIAL_RATES_JSON_CLASSPATH + " dosyasından başlangıç kurları yüklenirken hata", ex);
            initialRates = Collections.emptyList();
            throw new RuntimeException("Başlangıç kurları yüklenirken hata", ex);
        } finally {
            if (jsonInput != null) {
                try {
                    jsonInput.close();
                } catch (IOException e) {
                    log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, null, 
                            "Başlangıç kurları JSON giriş akışı kapatılırken hata");
                }
            }
        }
    }

    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "8081"));
    }

    public long getPublishIntervalMs() {
        return Long.parseLong(properties.getProperty("publish.interval.ms", "1000"));
    }

    public double getFluctuationVolatility() {
        return Double.parseDouble(properties.getProperty("fluctuation.volatility", "0.0005"));
    }
     public int getFluctuationMaxRetries() {
        return Integer.parseInt(properties.getProperty("fluctuation.max.retries", "10"));
    }

    public double getMinSpread() {
        return Double.parseDouble(properties.getProperty("fluctuation.min.spread", "0.0001"));
    }

    public List<Rate> getInitialRates() {
        // Orijinal listenin değiştirilmesini önlemek için kopyalar döndür
        return initialRates.stream().map(Rate::copy).toList();
    }

    // Security Configuration Methods
    public String getSecurityUsername() {
        // Priority: TCP_PROVIDER_USER -> APP_SECURITY_USERNAME -> properties -> default
        String username = System.getenv("TCP_PROVIDER_USER");
        if (username == null || username.trim().isEmpty()) {
            username = System.getenv("APP_SECURITY_USERNAME");
        }
        if (username == null || username.trim().isEmpty()) {
            username = properties.getProperty("app.security.username", "defaultuser");
        }
        return username.trim();
    }

    public String getSecurityPassword() {
        // Priority: TCP_PROVIDER_PASSWORD -> APP_SECURITY_PASSWORD -> properties -> default  
        String password = System.getenv("TCP_PROVIDER_PASSWORD");
        if (password == null || password.trim().isEmpty()) {
            password = System.getenv("APP_SECURITY_PASSWORD");
        }
        if (password == null || password.trim().isEmpty()) {
            password = properties.getProperty("app.security.password", "defaultpass");
        }
        return password.trim();
    }
}
