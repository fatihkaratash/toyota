package com.toyota.tcpserver.network;

import com.toyota.tcpserver.model.Rate;
import com.toyota.tcpserver.event.RateUpdateListener;
import com.toyota.tcpserver.service.RatePublisher;
import com.toyota.tcpserver.logging.LoggingHelper;
import com.toyota.tcpserver.config.ConfigurationReader;
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
    private static final String AUTH_PREFIX = "AUTH|";
    private static final String AUTH_SUCCESS_RESPONSE = "OK|Authenticated";
    private static final String AUTH_FAILED_RESPONSE = "ERROR|Authentication failed";
    private static final String AUTH_FORMAT_ERROR_RESPONSE = "ERROR|Invalid authentication format";
    
    private final Socket clientSocket;
    private final RatePublisher ratePublisher;
    private final ConfigurationReader configurationReader;
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = true;
    private boolean authenticated = false;

    public ClientHandler(Socket socket, RatePublisher ratePublisher, ConfigurationReader configurationReader) {
        this.clientSocket = socket;
        this.ratePublisher = ratePublisher;
        this.configurationReader = configurationReader;
        try {
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            log.error(LoggingHelper.PLATFORM_TCP, null, 
                   "ClientHandler: " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisi için akışlar ayarlanırken hata: " + e.getMessage(), e);
            running = false;
        }
        
        // RatePublisher listener registration will be done after authentication
    }

    @Override
    public void run() {
        if (!running) return;

        log.info(LoggingHelper.OPERATION_CONNECT, LoggingHelper.PLATFORM_TCP, null, 
                "İstemci bağlandı: " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " - Authentication bekleniyor");
        
        try {
            // First handle authentication
            if (!handleAuthentication()) {
                log.warn(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                        "Authentication başarısız, bağlantı kapatılıyor: " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());
                return;
            }

            log.info(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                    "Authentication başarılı: " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());

            // Now register with RatePublisher after successful authentication
            if (ratePublisher != null) {
                ratePublisher.addListener(this);
                log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null,
                        clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " dinleyici olarak RatePublisher'a eklendi.");
            }

            // Handle normal TCP communication
            String inputLine;
            while (running && (inputLine = in.readLine()) != null) {
                log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, null, 
                        clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " adresinden alındı: " + inputLine);
                processCommand(inputLine);
            }
        } catch (SocketException e) {
            if (running) { 
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
                ratePublisher.removeListener(this);
            }
            closeConnection();
        }
    }

    private boolean handleAuthentication() throws IOException {
        log.debug(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                "Authentication süreci başlatılıyor: " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());

        // Read the first message from client - must be AUTH message
        String authMessage = in.readLine();
        log.info(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                "Authentication mesajı alındı: " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " - Ham mesaj: '" + authMessage + "'");

        if (authMessage == null) {
            log.warn(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                    "Authentication başarısız: mesaj alınamadı - " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());
            out.println(AUTH_FAILED_RESPONSE);
            return false;
        }

        // Check if message starts with AUTH|
        if (!authMessage.startsWith(AUTH_PREFIX)) {
            log.warn(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                    "Authentication başarısız: mesaj AUTH| ile başlamıyor - " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " - Alınan: '" + authMessage + "'");
            out.println(AUTH_FORMAT_ERROR_RESPONSE);
            return false;
        }

        // Parse the authentication message
        String[] parts = authMessage.split("\\|");
        if (parts.length != 3) {
            log.warn(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                    "Authentication başarısız: geçersiz mesaj formatı - beklenen 3 parça, alınan: " + parts.length + " - " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());
            out.println(AUTH_FORMAT_ERROR_RESPONSE);
            return false;
        }

        // Extract and clean credentials
        String receivedUsername = parts[1].trim();
        String receivedPassword = parts[2].trim().replaceAll("[\r\n]", "");

        log.debug(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                "Kimlik bilgileri ayrıştırıldı - Kullanıcı: '" + receivedUsername + "', Şifre uzunluğu: " + receivedPassword.length() + " - " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());

        // Get expected credentials from configuration
        String expectedUsername = configurationReader.getSecurityUsername();
        String expectedPassword = configurationReader.getSecurityPassword();

        // Authenticate using equals() method (not ==)
        boolean usernameMatch = expectedUsername.equals(receivedUsername);
        boolean passwordMatch = expectedPassword.equals(receivedPassword);
        boolean isAuthenticated = usernameMatch && passwordMatch;

        log.debug(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                "Kimlik bilgisi karşılaştırması - Username match: " + usernameMatch + ", Password match: " + passwordMatch + " - " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());

        if (isAuthenticated) {
            out.println(AUTH_SUCCESS_RESPONSE);
            authenticated = true;
            log.info(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                    "TCP authentication başarılı - Kullanıcı: '" + receivedUsername + "' - " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());
            return true;
        } else {
            out.println(AUTH_FAILED_RESPONSE);
            log.warn(LoggingHelper.OPERATION_AUTH, LoggingHelper.PLATFORM_TCP, null,
                    "TCP authentication başarısız - Kullanıcı: '" + receivedUsername + "' - " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());
            return false;
        }
    }

    private void processCommand(String command) {
        if (!authenticated) {
            log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_TCP, null,
                    "Komut işleme reddedildi: istemci authenticated değil - " + clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode());
            out.println("ERROR|Authentication required");
            return;
        }

        String[] parts = command.split("\\|",2);
        if (parts.length == 0) {
            out.println("ERROR|Geçersiz komut formatı");
            return;
        }

        String action = parts[0].trim().toLowerCase();
        String rateNameInput = (parts.length > 1) ? parts[1].trim() : null; 
        String rateName = (rateNameInput != null) ? rateNameInput.toUpperCase() : null;

        log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, rateName,
                clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " processCommand: action=" + action + ", rateName=" + rateName + (rateNameInput != null && !rateNameInput.equals(rateName) ? " (original: " + rateNameInput + ")" : ""));

        switch (action) {
            case "subscribe":
                if (rateName != null && !rateName.isEmpty()) {
                    if (ratePublisher.isValidRatePair(rateName)) {
                        boolean added = subscriptions.add(rateName);
                        log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_TCP, rateName,
                                clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " 'subscribe' için '" + rateName + "' eklendi mi: " + added + ". Güncel abonelikler: " + subscriptions);
                        out.println("Şuna abone olundu: " + rateName);
                        
                        // Abonelik üzerine mevcut kuru hemen gönder
                        Rate currentRate = ratePublisher.getCurrentRate(rateName); 
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

        //rate is also checked consistently (uppercase)
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

            String rateInfo = String.format("BID:%.5f ASK:%.5f", rate.getBid(), rate.getAsk());
            log.trace(LoggingHelper.OPERATION_UPDATE, LoggingHelper.PLATFORM_PF1, rate.getPairName(), rateInfo, // PF1 platformu logu, TCP üzerinden gönderim için
                    clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " adresine gönderildi");
            
            if (out.checkError()) { 
                log.error(LoggingHelper.PLATFORM_TCP, rate.getPairName(), 
                        clientSocket.getRemoteSocketAddress() + " ID: " + this.hashCode() + " istemcisine mesaj gönderilirken hata. Bağlantı kapatılıyor.");
                stopHandler(); // Gönderme başarısız olursa durdur ve kapat
            }
        }
    }
    
    @Override
    public boolean isSubscribedTo(String pairName) {

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

    public boolean isAuthenticated() {
        return authenticated;
    }
}
