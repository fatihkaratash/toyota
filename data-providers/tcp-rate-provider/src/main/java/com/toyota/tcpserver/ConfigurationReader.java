package com.toyota.tcpserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class ConfigurationReader {
    private static final Logger logger = LogManager.getLogger(ConfigurationReader.class);
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
                logger.error("Üzgünüz, {} bulunamadı", PROPERTIES_FILE);
                throw new RuntimeException("Yapılandırma dosyası " + PROPERTIES_FILE + " sınıf yolunda bulunamadı.");
            }
            properties.load(input);
            logger.info("{} dosyasından yapılandırma başarıyla yüklendi", PROPERTIES_FILE);
        } catch (IOException ex) {
            logger.error(PROPERTIES_FILE + " dosyasından yapılandırma yüklenirken hata", ex);
            throw new RuntimeException(PROPERTIES_FILE + " dosyasından yapılandırma yüklenirken hata", ex);
        }
    }

    private void loadInitialRates() {
        ObjectMapper mapper = new ObjectMapper();
        InputStream jsonInput = null;
        try {
            // Önce dosya sisteminden yüklemeyi dene (örn. IDE'den veya paketlenmemiş JAR'dan çalıştırırken)
            if (Files.exists(Paths.get(INITIAL_RATES_JSON_FILESYSTEM))) {
                jsonInput = Files.newInputStream(Paths.get(INITIAL_RATES_JSON_FILESYSTEM));
                logger.info("Başlangıç kurları dosya sisteminden yükleniyor: {}", INITIAL_RATES_JSON_FILESYSTEM);
            }
            // Sınıf yoluna geri dön (örn. uber JAR'dan çalıştırılırken)
            else {
                 jsonInput = getClass().getClassLoader().getResourceAsStream(INITIAL_RATES_JSON_CLASSPATH);
                 if (jsonInput == null) { // sınıf yolu için "config/" önekiyle dene
                     jsonInput = getClass().getClassLoader().getResourceAsStream("config/" + INITIAL_RATES_JSON_CLASSPATH);
                 }

                if (jsonInput == null) {
                    logger.error("Üzgünüz, {} sınıf yolunda veya dosya sisteminde bulunamadı.", INITIAL_RATES_JSON_CLASSPATH);
                    throw new FileNotFoundException(INITIAL_RATES_JSON_CLASSPATH + " bulunamadı.");
                }
                logger.info("Başlangıç kurları sınıf yolundan yükleniyor: {}", INITIAL_RATES_JSON_CLASSPATH);
            }

            initialRates = mapper.readValue(jsonInput, new TypeReference<List<Rate>>() {});
            initialRates.forEach(rate -> {
                if (rate.getTimestamp() == null || rate.getTimestamp().isEmpty()) {
                    rate.setCurrentTimestamp();
                }
            });
            logger.info("Başarıyla {} başlangıç kuru yüklendi.", initialRates.size());

        } catch (IOException ex) {
            logger.error(INITIAL_RATES_JSON_CLASSPATH + " dosyasından başlangıç kurları yüklenirken hata", ex);
            initialRates = Collections.emptyList();
            throw new RuntimeException("Başlangıç kurları yüklenirken hata", ex);
        } finally {
            if (jsonInput != null) {
                try {
                    jsonInput.close();
                } catch (IOException e) {
                    logger.warn("Başlangıç kurları JSON giriş akışı kapatılırken hata", e);
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
}
