package com.toyota.tcpserver;

import com.toyota.tcpserver.logging.LoggingHelper;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TcpServer {
    private static final LoggingHelper log = new LoggingHelper(TcpServer.class);

    private final int port;
    private final ConfigurationReader configurationReader;
    private final RatePublisher ratePublisher;
    private ServerSocket serverSocket;
    private final ExecutorService clientExecutorService;
    // Thread-safe istemci işleyicileri listesi kullan
    private final List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private Thread serverThread;


    public TcpServer(ConfigurationReader configReader) {
        this.configurationReader = configReader;
        this.port = configurationReader.getServerPort();
        this.clientExecutorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ClientHandlerThread-" + System.currentTimeMillis());
            t.setDaemon(true); // Sadece daemon thread'ler çalışıyorsa JVM'in çıkmasına izin ver
            return t;
        });
        // Paylaşılan clientHandlers listesini RatePublisher'a geçir
        this.ratePublisher = new RatePublisher(configurationReader, clientHandlers);
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
        ratePublisher.start(); // Kur yayıncısını başlat
    }

    private void runServerLoop() {
        try {
            serverSocket = new ServerSocket(port);
            log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_TCP, null, 
                    "TCP Sunucusu " + port + " portunda başlatıldı");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept(); // Bir bağlantı yapılana kadar bloklar
                    log.info(LoggingHelper.OPERATION_CONNECT, LoggingHelper.PLATFORM_TCP, null, 
                            "Yeni istemci bağlantısı kabul edildi: " + clientSocket.getRemoteSocketAddress());
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket, ratePublisher);
                    clientHandlers.add(clientHandler);
                    clientExecutorService.submit(clientHandler);
                    
                    // Periyodik olarak kullanılmayan istemci işleyicilerini temizle
                    cleanupClientHandlers();

                } catch (IOException e) {
                    if (running) { // Sadece sunucunun çalışması gerekiyorsa logla
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
            // Döngü beklenmedik bir şekilde çıkarsa ve çalışma devam ediyorsa kaynakların temizlenmesini sağla
            if (running) {
                stopServerInternally();
            }
        }
    }
    
    private void cleanupClientHandlers() {
        clientHandlers.removeIf(handler -> !handler.isRunning());
        log.trace(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, null, 
                "Aktif istemci işleyicileri: " + clientHandlers.size());
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
        running = false; // Tüm döngülere durma sinyali gönder

        if (ratePublisher != null) {
            ratePublisher.stop();
        }

        // Tüm istemci işleyicilerini durdur
        log.info(LoggingHelper.OPERATION_STOP, LoggingHelper.PLATFORM_TCP, null, 
                "Tüm istemci işleyicileri durduruluyor...");
        for (ClientHandler handler : clientHandlers) {
            handler.stopHandler();
        }
        clientHandlers.clear(); // Durdurduktan sonra listeyi temizle

        // İstemci yürütücü servisini kapat
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

        // Sunucu soketini kapat
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
