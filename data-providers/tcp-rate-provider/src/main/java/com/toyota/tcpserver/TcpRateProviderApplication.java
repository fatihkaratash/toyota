package com.toyota.tcpserver;

import com.toyota.tcpserver.config.ConfigurationReader;
import com.toyota.tcpserver.logging.LoggingHelper;
import com.toyota.tcpserver.network.TcpServer;
import org.apache.logging.log4j.LogManager;

public class TcpRateProviderApplication {
    private static final LoggingHelper log = new LoggingHelper(TcpRateProviderApplication.class);

    public static void main(String[] args) {
        // Log4j2 yapılandırmasını yükle        
        log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_TCP, null, 
                "TCP Kur Sağlayıcı Uygulaması başlatılıyor...");

        try {
            ConfigurationReader configReader = new ConfigurationReader();
            
            // Log security configuration at application startup
            log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Kur Sağlayıcı Uygulaması başlatılıyor - Authentication aktif...");
            
            final TcpServer server = new TcpServer(configReader);
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info(LoggingHelper.OPERATION_STOP, LoggingHelper.PLATFORM_TCP, null, 
                        "Kapatma işlemi tetiklendi. TCP Sunucusu durduruluyor...");
                server.stop();
                LogManager.shutdown(); // Shutdown Log4j2
                log.info(LoggingHelper.OPERATION_STOP, LoggingHelper.PLATFORM_TCP, null, 
                        "TCP Sunucusu ve loglama düzgün bir şekilde kapatıldı.");
            }, "ShutdownHookThread"));

            server.start();
            log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Kur Sağlayıcı Uygulaması başarıyla başlatıldı.");

        } catch (Exception e) {
            log.fatal(LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Kur Sağlayıcı Uygulaması başlatılamadı", e);
            LogManager.shutdown();
            System.exit(1);
        }
    }
}
