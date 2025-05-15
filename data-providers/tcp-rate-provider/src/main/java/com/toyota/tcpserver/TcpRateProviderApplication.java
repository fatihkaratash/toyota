package com.toyota.tcpserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TcpRateProviderApplication {
    private static final Logger logger = LogManager.getLogger(TcpRateProviderApplication.class);

    public static void main(String[] args) {
        // Log4j2 yapılandırmasını yükle        
        logger.info("TCP Kur Sağlayıcı Uygulaması başlatılıyor...");

        try {
            ConfigurationReader configReader = new ConfigurationReader();
            final TcpServer server = new TcpServer(configReader);
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Kapatma işlemi tetiklendi. TCP Sunucusu durduruluyor...");
                server.stop();
                LogManager.shutdown(); // Shutdown Log4j2
                logger.info("TCP Sunucusu ve loglama düzgün bir şekilde kapatıldı.");
            }, "ShutdownHookThread"));

            server.start();
            logger.info("TCP Kur Sağlayıcı Uygulaması başarıyla başlatıldı.");

        } catch (Exception e) {
            logger.fatal("TCP Kur Sağlayıcı Uygulaması başlatılamadı", e);
            LogManager.shutdown();
            System.exit(1);
        }
    }
}
