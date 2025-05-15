package com.toyota.tcpserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private static final Logger logger = LogManager.getLogger(ClientHandler.class);
    private final Socket clientSocket;
    private final RatePublisher ratePublisher; // Abone olma üzerine ilk kuru göndermek için
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet(); // Abonelikler için thread-safe set
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, RatePublisher ratePublisher) {
        this.clientSocket = socket;
        this.ratePublisher = ratePublisher;
        try {
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            logger.error("ClientHandler: {} istemcisi için akışlar ayarlanırken hata: {}", clientSocket.getRemoteSocketAddress(), e.getMessage(), e);
            running = false; // Kurulum başarısız olursa run() metodunun ilerlemesini engelle
            closeConnection();
        }
    }

    @Override
    public void run() {
        if (!running) return; // Kurulum başarısız olduysa çık

        logger.info("İstemci bağlandı: {}", clientSocket.getRemoteSocketAddress());
        try {
            String inputLine;
            while (running && (inputLine = in.readLine()) != null) {
                logger.debug("{} adresinden alındı: {}", clientSocket.getRemoteSocketAddress(), inputLine);
                processCommand(inputLine);
            }
        } catch (SocketException e) {
            if (running) { // Sadece kasıtlı kapatılmadıysa logla
                 logger.warn("{} istemcisi için SocketException: {} (İstemci muhtemelen bağlantıyı kesti)", clientSocket.getRemoteSocketAddress(), e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                logger.error("{} istemcisi için IOException: {}", clientSocket.getRemoteSocketAddress(), e.getMessage(), e);
            }
        } finally {
            logger.info("{} istemcisi bağlantısı kesildi veya işleyici durduruluyor.", clientSocket.getRemoteSocketAddress());
            closeConnection();
            // İsteğe bağlı olarak, TcpServer'ı bu işleyiciyi listesinden kaldırmak için bilgilendir
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split("\\|");
        if (parts.length == 0) {
            out.println("ERROR|Geçersiz komut formatı");
            return;
        }

        String action = parts[0].trim().toLowerCase();
        String rateName = (parts.length > 1) ? parts[1].trim().toUpperCase() : null;

        switch (action) {
            case "subscribe":
                if (rateName != null && !rateName.isEmpty()) {
                    if (ratePublisher.isValidRatePair(rateName)) {
                        subscriptions.add(rateName);
                        out.println("Şuna abone olundu: " + rateName);
                        logger.info("{} istemcisi {} kuruna abone oldu", clientSocket.getRemoteSocketAddress(), rateName);
                        // Abonelik üzerine mevcut kuru hemen gönder
                        Rate currentRate = ratePublisher.getCurrentRate(rateName);
                        if (currentRate != null) {
                            sendRateUpdate(currentRate);
                        } else {
                            logger.warn("Anlık abonelik isteği üzerine {} için mevcut kur bulunamadı.", rateName);
                        }
                    } else {
                        out.println("ERROR|Geçersiz veya bilinmeyen kur adı: " + rateName);
                        logger.warn("{} istemcisi geçersiz kura abone olmaya çalıştı: {}", clientSocket.getRemoteSocketAddress(), rateName);
                    }
                } else {
                    out.println("ERROR|Subscribe komutu bir kur adı gerektirir (örn. subscribe|PF1_USDTRY)");
                }
                break;
            case "unsubscribe":
                if (rateName != null && !rateName.isEmpty()) {
                    subscriptions.remove(rateName);
                    out.println("Şundan abonelik kaldırıldı: " + rateName);
                    logger.info("{} istemcisi {} kurundan aboneliğini kaldırdı", clientSocket.getRemoteSocketAddress(), rateName);
                } else {
                    out.println("ERROR|Unsubscribe komutu bir kur adı gerektirir (örn. unsubscribe|PF1_USDTRY)");
                }
                break;
            default:
                out.println("ERROR|Bilinmeyen komut: " + action);
                logger.warn("{} istemcisi bilinmeyen komut gönderdi: {}", clientSocket.getRemoteSocketAddress(), action);
        }
    }

    public void sendRateUpdate(Rate rate) {
        if (rate == null || clientSocket.isClosed() || out == null) {
            return;
        }
        if (subscriptions.contains(rate.getPairName())) {
            // Format: PAIR_NAME|22:number:BID_VALUE|25:number:ASK_VALUE|5:timestamp:TIMESTAMP_VALUE
            String message = String.format("%s|22:number:%.8f|25:number:%.8f|5:timestamp:%s",
                    rate.getPairName(), rate.getBid(), rate.getAsk(), rate.getTimestamp());
            out.println(message);
            logger.trace("{} adresine gönderildi: {}", clientSocket.getRemoteSocketAddress(), message);
            if (out.checkError()) { // PrintWriter'ın hata ile karşılaşıp karşılaşmadığını kontrol et
                logger.error("{} istemcisine mesaj gönderilirken hata. Bağlantı kapatılıyor.", clientSocket.getRemoteSocketAddress());
                stopHandler(); // Gönderme başarısız olursa durdur ve kapat
            }
        }
    }
    
    public boolean isSubscribedTo(String pairName) {
        return subscriptions.contains(pairName);
    }

    public void stopHandler() {
        running = false;
        closeConnection();
    }

    private void closeConnection() {
        try {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.error("{} istemcisi için bağlantı kapatılırken hata: {}",
                         (clientSocket != null ? clientSocket.getRemoteSocketAddress() : "N/A"), e.getMessage());
        }
    }

    public boolean isRunning() {
        return running && clientSocket != null && !clientSocket.isClosed();
    }
}
