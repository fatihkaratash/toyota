package com.toyota.tcpserver;

import com.toyota.tcpserver.logging.LoggingHelper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable, RateUpdateListener {
    private static final LoggingHelper log = new LoggingHelper(ClientHandler.class);
    private final Socket clientSocket;
    private final RatePublisher ratePublisher;
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
            log.error(LoggingHelper.PLATFORM_TCP, null, 
                   "ClientHandler: " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisi için akışlar ayarlanırken hata: " + e.getMessage(), e);
            running = false; // Kurulum başarısız olursa run() metodunun ilerlemesini engelle
            // closeConnection(); // Constructor'da closeConnection çağrısı riskli olabilir, zaten running false olacak.
        }
        
        // Başlangıçta kendini RatePublisher'a dinleyici olarak kaydet
        if (ratePublisher != null && running) {
            ratePublisher.addListener(this); // RatePublisher'a dinleyici olarak ekleniyor
            log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null,
                    clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " dinleyici olarak RatePublisher'a eklendi.");
        }
    }

    @Override
    public void run() {
        if (!running) return; // Kurulum başarısız olduysa çık

        log.info(LoggingHelper.OPERATION_CONNECT, LoggingHelper.PLATFORM_TCP, null, 
                "İstemci bağlandı: " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());
        try {
            String inputLine;
            while (running && (inputLine = in.readLine()) != null) {
                log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, 
                        clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " adresinden alındı: " + inputLine);
                processCommand(inputLine);
            }
        } catch (SocketException e) {
            if (running) { // Sadece kasıtlı kapatılmadıysa logla
                 log.warn(LoggingHelper.OPERATION_DISCONNECT, LoggingHelper.PLATFORM_TCP, null, 
                         clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisi için SocketException: " + e.getMessage() + " (İstemci muhtemelen bağlantıyı kesti)");
            }
        } catch (IOException e) {
            if (running) {
                log.error(LoggingHelper.PLATFORM_TCP, null, 
                        clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisi için IOException: " + e.getMessage(), e);
            }
        } finally {
            log.info(LoggingHelper.OPERATION_DISCONNECT, LoggingHelper.PLATFORM_TCP, null, 
                    clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisi bağlantısı kesildi veya işleyici durduruluyor.");
            // RatePublisher'dan dinleyici kaydını kaldır
            if (ratePublisher != null) {
                ratePublisher.removeListener(this); // RatePublisher'dan dinleyici olarak kaldırılıyor
            }
            closeConnection();
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split("\\|",2);
        if (parts.length == 0) {
            out.println("ERROR|Geçersiz komut formatı");
            return;
        }

        String action = parts[0].trim().toLowerCase();
        String rateNameInput = (parts.length > 1) ? parts[1].trim() : null; // Keep original case for logging if needed, but use uppercase for logic
        
        // Use uppercase for all internal operations and comparisons
        String rateName = (rateNameInput != null) ? rateNameInput.toUpperCase() : null;

        log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, rateName,
                clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " processCommand: action=" + action + ", rateName=" + rateName + (rateNameInput != null && !rateNameInput.equals(rateName) ? " (original: " + rateNameInput + ")" : ""));

        switch (action) {
            case "subscribe":
                if (rateName != null && !rateName.isEmpty()) {
                    if (ratePublisher.isValidRatePair(rateName)) { // isValidRatePair should also handle case consistently if needed, or expect uppercase
                        boolean added = subscriptions.add(rateName);
                        log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, rateName,
                                clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " 'subscribe' için '" + rateName + "' eklendi mi: " + added + ". Güncel abonelikler: " + subscriptions);
                        out.println("Şuna abone olundu: " + rateName);
                        
                        // Abonelik üzerine mevcut kuru hemen gönder
                        Rate currentRate = ratePublisher.getCurrentRate(rateName); // getCurrentRate should expect uppercase or handle case
                        if (currentRate != null) {
                            log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, rateName,
                                    clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " abonelik üzerine anlık kur gönderiliyor: " + rateName);
                            sendRateUpdate(currentRate);
                        } else {
                            log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, rateName, 
                                    clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " anlık abonelik isteği üzerine mevcut kur bulunamadı: " + rateName);
                        }
                    } else {
                        out.println("ERROR|Geçersiz veya bilinmeyen kur adı: " + rateName);
                        log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, rateName, 
                                clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisi geçersiz kura abone olmaya çalıştı: " + rateName);
                    }
                } else {
                    out.println("ERROR|Subscribe komutu bir kur adı gerektirir (örn. subscribe|PF1_USDTRY)");
                }
                break;
            case "unsubscribe":
                if (rateName != null && !rateName.isEmpty()) {
                    boolean removed = subscriptions.remove(rateName);
                    out.println("Şundan abonelik kaldırıldı: " + rateName);
                    log.info(LoggingHelper.OPERATION_UNSUBSCRIBE, LoggingHelper.PLATFORM_TCP, rateName, 
                            clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisi '" + rateName + "' kurundan aboneliğini kaldırdı (kaldırıldı mı: " + removed + "). Güncel abonelikler: " + subscriptions);
                } else {
                    out.println("ERROR|Unsubscribe komutu bir kur adı gerektirir (örn. unsubscribe|PF1_USDTRY)");
                }
                break;
            default:
                out.println("ERROR|Bilinmeyen komut: " + action);
                log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, null, 
                        clientSocket.getRemoteSocketAddress() + " istemcisi bilinmeyen komut gönderdi: " + action);
        }
    }

    @Override
    public void onRateUpdate(Rate rate) {
        log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, rate.getPairName(),
                clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " onRateUpdate çağrıldı: " + rate.getPairName());
        sendRateUpdate(rate);
    }

    public void sendRateUpdate(Rate rate) {
        if (rate == null || clientSocket.isClosed() || out == null) {
            log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, (rate != null ? rate.getPairName() : "N/A"),
                    clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " sendRateUpdate: Gönderim koşulları sağlanamadı (rate null, socket kapalı veya out null).");
            return;
        }

        // Ensure pairName from rate is also checked consistently (e.g. uppercase)
        // Assuming rate.getPairName() returns it in the canonical (uppercase) format.
        // If not, it should be: String pairNameForCheck = rate.getPairName().toUpperCase();
        boolean isSubscribed = subscriptions.contains(rate.getPairName()); 
        log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, rate.getPairName(),
                clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " sendRateUpdate: '" + rate.getPairName() + "' için abone mi: " + isSubscribed + ". Abonelikler: " + subscriptions);

        if (isSubscribed) {
            // Format: PAIR_NAME|22:number:BID_VALUE|25:number:ASK_VALUE|5:timestamp:TIMESTAMP_VALUE
            String message = String.format("%s|22:number:%.8f|25:number:%.8f|5:timestamp:%s",
                    rate.getPairName(), rate.getBid(), rate.getAsk(), rate.getTimestamp());
            log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, rate.getPairName(),
                    clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " gönderilecek mesaj: " + message);
            out.println(message);
            
            // Kur bilgisini log için hazırla
            String rateInfo = String.format("BID:%.5f ASK:%.5f", rate.getBid(), rate.getAsk());
            log.trace(LoggingHelper.OPERATION_UPDATE, LoggingHelper.PLATFORM_PF1, rate.getPairName(), rateInfo, // PF1 platformu logu, TCP üzerinden gönderim için
                    clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " adresine gönderildi");
            
            if (out.checkError()) { // PrintWriter'ın hata ile karşılaşıp karşılaşmadığını kontrol et
                log.error(LoggingHelper.PLATFORM_TCP, rate.getPairName(), 
                        clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisine mesaj gönderilirken hata. Bağlantı kapatılıyor.");
                stopHandler(); // Gönderme başarısız olursa durdur ve kapat
            }
        }
    }
    
    @Override
    public boolean isSubscribedTo(String pairName) {
        // Ensure consistent casing for checking subscriptions.
        String pairNameToCheck = (pairName != null) ? pairName.toUpperCase() : null;
        boolean subscribed = subscriptions.contains(pairNameToCheck);
        log.trace(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, pairNameToCheck,
                "isSubscribedTo", clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " isSubscribedTo('" + pairNameToCheck + "'): " + subscribed + ". Abonelikler: " + subscriptions);
        return subscribed;
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
                log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null,
                        "ClientHandler ID: " + this.hashCode() + " socket kapatıldı.");
            }
        } catch (IOException e) {
            log.error(LoggingHelper.PLATFORM_TCP, null,
                     (clientSocket != null ? clientSocket.getRemoteSocketAddress() : "N/A") + " ID: " + this.hashCode() + " istemcisi için bağlantı kapatılırken hata: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running && clientSocket != null && !clientSocket.isClosed();
    }
}
