package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.config.SubscriberProperties;
import com.toyota.mainapp.logging.LoggingHelper;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.model.RateStatus;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TcpRateSubscriber extends AbstractRateSubscriber {

    private final SubscriberProperties.TcpSubscriberProperties properties;
    
    private ExecutorService executorService;
    private Socket clientSocket;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean connectionEstablished = new AtomicBoolean(false);

    @Autowired
    public TcpRateSubscriber(SubscriberProperties subscriberProperties) {
        super();
        this.properties = subscriberProperties.getTcp();
    }
    
    public TcpRateSubscriber() {
        // Default constructor for use when not managed by Spring
        super();
        this.properties = new SubscriberProperties.TcpSubscriberProperties();
    }

    @Override
    protected String getSubscriberType() {
        return "TCP Abonesi";
    }
    
    @Override
    protected void initializeResources() {
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "tcp-subscriber-" + definition.getName());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    protected void startSubscriptionInternal() {
        executorService.submit(this::connectAndListen);
    }

    private void connectAndListen() {
        reconnectAttempts.set(0);
        while (active.get()) {
            try {
                int currentAttempt = reconnectAttempts.get();
                if (currentAttempt > 0) {
                    int delaySeconds = calculateReconnectDelay(currentAttempt);
                    logger.info("{} {}: Yeniden bağlanma denemesi {} - {} saniye bekleniyor", 
                               getSubscriberType(), definition.getName(), currentAttempt, delaySeconds);
                    callback.onStatusChange(definition.getName(), "Yeniden bağlanma denemesi " + currentAttempt);
                    
                    if (handleReconnectDelay(delaySeconds)) return; // Interrupted or inactive
                }
                
                establishConnection();
                processMessages();
                
            } catch (SocketTimeoutException e) {
                handleConnectionError("Soket zaman aşımı", e);
            } catch (IOException e) {
                if (active.get()) {
                    handleConnectionError("Bağlantı hatası", e);
                } else {
                    logger.info("{} {}: Abone durdurulduğu için soket kapatıldı", getSubscriberType(), definition.getName());
                }
            } finally {
                connectionEstablished.set(false);
                closeSocket();
                if (active.get()) { // Only increment if still active and intending to reconnect
                    reconnectAttempts.incrementAndGet();
                }
            }
        }
        logger.info("{} {} dinleyici iş parçacığı durduruldu", getSubscriberType(), definition.getName());
        callback.onStatusChange(definition.getName(), "Abonelik durduruldu");
    }

    private boolean handleReconnectDelay(int delaySeconds) {
        try {
            TimeUnit.SECONDS.sleep(delaySeconds);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.warn("{} {}: Yeniden bağlanma beklemesi kesildi", getSubscriberType(), definition.getName());
            return true; // Indicates interruption
        }
        if (!active.get()) {
            logger.info("{} {}: Abonelik durduruluyor, yeniden bağlanma iptal edildi", getSubscriberType(), definition.getName());
            return true; // Indicates inactive
        }
        return false; // Continue
    }

    private void establishConnection() throws IOException {
        String host = getConnectionHost();
        int port = getConnectionPort();
        int connectionTimeout = getConnectionTimeout();
        
        logger.info("{} {}: Bağlanılıyor {}:{} (timeout: {}ms)", 
                   getSubscriberType(), definition.getName(), host, port, connectionTimeout);
        
        clientSocket = new Socket();
        clientSocket.connect(new java.net.InetSocketAddress(host, port), connectionTimeout);
        clientSocket.setSoTimeout(getReadTimeout());
        
        connectionEstablished.set(true);
        reconnectAttempts.set(0); 
        callback.onStatusChange(definition.getName(), "Bağlantı başarılı: " + host + ":" + port);
        logger.info("{} {}: Bağlantı başarılı {}:{}", getSubscriberType(), definition.getName(), host, port);
    }

    private void processMessages() throws IOException {
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            for (String symbol : definition.getSubscribedSymbols()) {
                String subscribeMessage = formatSubscribeMessage(symbol);
                logger.info("{} {}: Abonelik gönderiliyor: {}", getSubscriberType(), definition.getName(), subscribeMessage);
                out.println(subscribeMessage);
            }

            String inputLine;
            while (active.get() && (inputLine = in.readLine()) != null) {
                logger.debug("{} {}: Ham veri alındı: {}", getSubscriberType(), definition.getName(), inputLine);
                try {
                    processRateData(inputLine);
                } catch (Exception e) {
                    logger.error("{} {}: Kur verisi ayrıştırılırken hata '{}'", getSubscriberType(), definition.getName(), inputLine, e);
                    callback.onError(definition.getName(), "Kur verisi ayrıştırma hatası: " + e.getMessage(), e);
                }
            }
        } finally {
             logger.info("{} {}: Soket bağlantısı kapandı/işlem sonlandı", getSubscriberType(), definition.getName());
        }
    }
    
    private void handleConnectionError(String errorType, Exception e) {
        if (active.get()) { // Log error only if subscriber is supposed to be active
            LoggingHelper.logError(logger, definition.getName(), getSubscriberType() + " - " + errorType, e.getMessage(), e);
            callback.onError(definition.getName(), errorType + ": " + e.getMessage(), e);
            callback.onStatusChange(definition.getName(), errorType + ". Yeniden bağlanılıyor...");
        }
    }
    
    private int calculateReconnectDelay(int attempt) {
        if (attempt <= 0) return 0;
        
        // Get retry attempts from config or use defaults
        int maxRetries = getMaxRetryAttempts();
        
        // If we've reached max retries and it's defined, use max delay
        if (maxRetries > 0 && attempt >= maxRetries) {
            return properties.getMaxReconnectDelaySeconds();
        }
        
        // Exponential backoff with jitter: min(maxDelay, initialDelay * 2^attempt) + random(0, 1000ms)
        int exponentialDelay = Math.min(
            properties.getMaxReconnectDelaySeconds(),
            properties.getInitialReconnectDelaySeconds() * (int)Math.pow(2, Math.min(attempt, 6)) // Cap at 2^6 to avoid overflow
        );
        
        // Add jitter (up to 1 second)
        int jitter = (int)(Math.random() * 1000) / 1000;
        return exponentialDelay + jitter;
    }
    
    private String formatSubscribeMessage(String symbol) {
        return findProperty("subscribeMessageFormat", String.class)
                .map(format -> format.replace("{symbol}", symbol))
                .orElse("subscribe|" + symbol);
    }
    
    private String getConnectionHost() {
        return definition.getHost();
    }
    
    private int getConnectionPort() {
        return definition.getPort() != null ? definition.getPort() : properties.getDefaultPort();
    }
    
    private int getConnectionTimeout() {
        return getProperty("connectionTimeoutMs", properties.getConnectionTimeoutMs());
    }
    
    private int getReadTimeout() {
        return getProperty("readTimeoutMs", properties.getReadTimeoutMs());
    }
    
    private int getMaxRetryAttempts() {
        return getProperty("retryAttempts", 0);
    }
    
    private void processRateData(String data) {
        parseRate(data).ifPresent(callback::onRateUpdate);
    }
    
    // PF1_USDTRY|22:number:34.401355|25:number:35.401355|5:timestamp:2024-12-15T11:31:34.509Z
    private Optional<Rate> parseRate(String data) {
        // Example parsing, needs to be robust
        String[] parts = data.split("\\|");
        if (parts.length < 4) {
            logger.warn("{} {}: Geçersiz veri formatı: {}", getSubscriberType(), definition.getName(), data);
            return Optional.empty();
        }
        String symbol = parts[0];
        if (!definition.getSubscribedSymbols().contains(symbol)) {
             logger.trace("{} {}: Abone olunmayan sembol için kur yoksayılıyor {}", getSubscriberType(), definition.getName(), symbol);
             return Optional.empty();
        }

        try {
            BigDecimal bid = new BigDecimal(parts[1].split(":")[2]);
            BigDecimal ask = new BigDecimal(parts[2].split(":")[2]);
            // Timestamp from TCP provider is expected to be ISO_INSTANT (e.g., "2024-12-15T11:31:34.509Z")
            Instant timestamp = Instant.parse(parts[3].split(":")[2]); 

            RateFields rateFields = new RateFields(bid, ask, timestamp);
            return Optional.of(new Rate(symbol, definition.getName(), rateFields, RateStatus.ACTIVE));
        } catch (NumberFormatException | DateTimeParseException | ArrayIndexOutOfBoundsException e) {
            logger.error("{} {}: '{}' içinden kur bileşenleri ayrıştırılamadı", 
                        getSubscriberType(), definition.getName(), data, e);
            return Optional.empty();
        }
    }

    private void closeSocket() {
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                clientSocket.close();
                logger.info("{} {}: Soket kapatıldı", getSubscriberType(), definition.getName());
            } catch (IOException e) {
                logger.error("{} {}: Soket kapatılırken hata", getSubscriberType(), definition.getName(), e);
            }
        }
        clientSocket = null;
    }

    @Override
    protected void stopSubscriptionInternal() {
        closeSocket(); 
        shutdownExecutorService();
    }

    private void shutdownExecutorService() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Checks if the subscriber is currently connected to the server.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connectionEstablished.get();
    }
    
    /**
     * Gets the current reconnection attempt count.
     * Resets to 0 upon successful connection.
     * 
     * @return The current reconnection attempt number
     */
    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }
}
