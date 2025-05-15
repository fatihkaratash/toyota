package com.toyota.tcpserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TcpServer {
    private static final Logger logger = LogManager.getLogger(TcpServer.class);

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
            logger.warn("TCP Sunucusu zaten {} portunda çalışıyor", port);
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
            logger.info("TCP Sunucusu {} portunda başlatıldı", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept(); // Bir bağlantı yapılana kadar bloklar
                    logger.info("Yeni istemci bağlantısı kabul edildi: {}", clientSocket.getRemoteSocketAddress());
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket, ratePublisher);
                    clientHandlers.add(clientHandler);
                    clientExecutorService.submit(clientHandler);
                    
                    // Periyodik olarak kullanılmayan istemci işleyicilerini temizle
                    cleanupClientHandlers();

                } catch (IOException e) {
                    if (running) { // Sadece sunucunun çalışması gerekiyorsa logla
                        logger.error("İstemci bağlantısı kabul edilirken hata: {}", e.getMessage(), e);
                    } else {
                        logger.info("TCP Sunucu soketi kapatıldı, yeni bağlantılar kabul edilmiyor.");
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.error("{} portunda TCP sunucusu başlatılamadı: {}", port, e.getMessage(), e);
            }
        } finally {
            logger.info("TCP Sunucu çalışma döngüsü sona erdi.");
            // Döngü beklenmedik bir şekilde çıkarsa ve çalışma devam ediyorsa kaynakların temizlenmesini sağla
            if (running) {
                stopServerInternally();
            }
        }
    }
    
    private void cleanupClientHandlers() {
        clientHandlers.removeIf(handler -> !handler.isRunning());
        logger.trace("Aktif istemci işleyicileri: {}", clientHandlers.size());
    }

    public void stop() {
        if (!running) {
            logger.warn("TCP Sunucusu çalışmıyor veya zaten durduruluyor.");
            return;
        }
        logger.info("TCP Sunucusu durduruluyor...");
        stopServerInternally();
        if (serverThread != null) {
            try {
                serverThread.join(5000); // Sunucu threadinin bitmesini bekle
                if (serverThread.isAlive()) {
                    logger.warn("Sunucu thread'i düzgün sonlandırılamadı, kesiliyor.");
                    serverThread.interrupt();
                }
            } catch (InterruptedException e) {
                logger.warn("Sunucu thread'inin durmasını beklerken kesildi.", e);
                Thread.currentThread().interrupt();
            }
        }
        logger.info("TCP Sunucusu başarıyla durduruldu.");
    }

    private void stopServerInternally() {
        running = false; // Tüm döngülere durma sinyali gönder

        if (ratePublisher != null) {
            ratePublisher.stop();
        }

        // Tüm istemci işleyicilerini durdur
        logger.info("Tüm istemci işleyicileri durduruluyor...");
        for (ClientHandler handler : clientHandlers) {
            handler.stopHandler();
        }
        clientHandlers.clear(); // Durdurduktan sonra listeyi temizle

        // İstemci yürütücü servisini kapat
        clientExecutorService.shutdown();
        try {
            if (!clientExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                clientExecutorService.shutdownNow();
                logger.warn("İstemci yürütücü servisi düzgün sonlandırılamadı.");
            }
        } catch (InterruptedException e) {
            clientExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Sunucu soketini kapat
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logger.info("Sunucu soketi kapatıldı.");
            } catch (IOException e) {
                logger.error("Sunucu soketi kapatılırken hata: {}", e.getMessage(), e);
            }
        }
    }
}
