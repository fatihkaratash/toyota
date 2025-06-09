package com.toyota.tcpserver.network;

import com.toyota.tcpserver.config.ConfigurationReader;
import com.toyota.tcpserver.logging.LoggingHelper;
import com.toyota.tcpserver.service.RatePublisher;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Toyota Financial Data Platform - TCP Server
 * 
 * Multi-threaded TCP server for financial rate data distribution with
 * authentication support. Manages client connections, rate publishing,
 * and graceful shutdown for the TCP rate provider service.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
public class TcpServer {
    private static final LoggingHelper log = new LoggingHelper(TcpServer.class);

    private final int port;
    private final ConfigurationReader configurationReader;
    private final RatePublisher ratePublisher;
    private ServerSocket serverSocket;
    private final ExecutorService clientExecutorService;
    // Thread-safe istemci
    private final List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private Thread serverThread;


    public TcpServer(ConfigurationReader configReader) {
        this.configurationReader = configReader;
        this.port = configurationReader.getServerPort();
        this.clientExecutorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ClientHandlerThread-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.ratePublisher = new RatePublisher(configurationReader, null);
        
        // Log security configuration at startup
        logSecurityConfiguration();
    }

    private void logSecurityConfiguration() {
        String username = configurationReader.getSecurityUsername();
        String password = configurationReader.getSecurityPassword();
        
        log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_TCP, null, 
                "TCP Server Security Configuration - Username: '" + username + "'");
        log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_TCP, null, 
                "TCP Server Security Configuration - Password configured: " + 
                (password != null && !password.isEmpty() && !"defaultpass".equals(password)));
        
        if ("defaultuser".equals(username) || "defaultpass".equals(password)) {
            log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Server kullanıyor default credentials! Lütfen APP_SECURITY_USERNAME ve APP_SECURITY_PASSWORD environment variables configure edin.");
        }
    }

    public void start() {
        if (running) {
            log.warn(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Sunucusu zaten " + port + " portunda çalışıyor");
            return;
        }
        running = true;
        serverThread = new Thread(this::runServerLoop, "TcpServerThread");
        serverThread.setDaemon(false); // Ana sunucu thread'i daemon olmamalı
        serverThread.start();
        ratePublisher.start(); 
    }

    private void runServerLoop() {
        try {
            serverSocket = new ServerSocket(port);
            log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Sunucusu " + port + " portunda başlatıldı - Authentication aktif");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log.info(LoggingHelper.OPERATION_CONNECT, LoggingHelper.PLATFORM_TCP, null, 
                            "Yeni istemci bağlantısı kabul edildi (Authentication bekleniyor): " + clientSocket.getRemoteSocketAddress());

                    ClientHandler clientHandler = new ClientHandler(clientSocket, ratePublisher, configurationReader);
                    clientHandlers.add(clientHandler);
                    clientExecutorService.submit(clientHandler);
                    cleanupClientHandlers();

                } catch (IOException e) {
                    if (running) { 
                        log.error(LoggingHelper.PLATFORM_TCP, null, 
                                "İstemci bağlantısı kabul edilirken hata: " + e.getMessage(), e);
                    } else {
                        log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, 
                                "TCP Sunucu soketi kapatıldı, yeni bağlantılar kabul edilmiyor.");
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                log.error(LoggingHelper.PLATFORM_TCP, null, 
                        port + " portunda TCP sunucusu başlatılamadı: " + e.getMessage(), e);
            }
        } finally {
            log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Sunucu çalışma döngüsü sona erdi.");
            // except
            if (running) {
                stopServerInternally();
            }
        }
    }
    
    private void cleanupClientHandlers() {
        clientHandlers.removeIf(handler -> !handler.isRunning());
    }

    public void stop() {
        if (!running) {
            log.warn(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Sunucusu çalışmıyor veya zaten durduruluyor.");
            return;
        }
        log.info(LoggingHelper.OPERATION_STOP, LoggingHelper.PLATFORM_TCP, null, 
                "TCP Sunucusu durduruluyor...");
        stopServerInternally();
        if (serverThread != null) {
            try {
                serverThread.join(5000); // Sunucu threadinin bitmesini bekle
                if (serverThread.isAlive()) {
                    log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, null, 
                            "Sunucu thread'i düzgün sonlandırılamadı, kesiliyor.");
                    serverThread.interrupt();
                }
            } catch (InterruptedException e) {
                log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, null, 
                        "Sunucu thread'inin durmasını beklerken kesildi.");
                Thread.currentThread().interrupt();
            }
        }
        log.info(LoggingHelper.OPERATION_STOP, LoggingHelper.PLATFORM_TCP, null, 
                "TCP Sunucusu başarıyla durduruldu.");
    }

    private void stopServerInternally() {
        running = false; 

        if (ratePublisher != null) {
            ratePublisher.stop();
        }
        log.info(LoggingHelper.OPERATION_STOP, LoggingHelper.PLATFORM_TCP, null, 
                "Tüm istemci işleyicileri durduruluyor...");
        for (ClientHandler handler : clientHandlers) {
            handler.stopHandler();
        }
        clientHandlers.clear(); // temizler

        clientExecutorService.shutdown();
        try {
            if (!clientExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                clientExecutorService.shutdownNow();
                log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, null, 
                        "İstemci yürütücü servisi düzgün sonlandırılamadı.");
            }
        } catch (InterruptedException e) {
            clientExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, 
                        "Sunucu soketi kapatıldı.");
            } catch (IOException e) {
                log.error(LoggingHelper.PLATFORM_TCP, null, 
                        "Sunucu soketi kapatılırken hata: " + e.getMessage(), e);
            }
        }
    }
}
