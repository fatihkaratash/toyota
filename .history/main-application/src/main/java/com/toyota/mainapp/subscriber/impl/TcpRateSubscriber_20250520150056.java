package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.model.RateStatus;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
import com.toyota.mainapp.util.LoggingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TcpRateSubscriber implements PlatformSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(TcpRateSubscriber.class);

    private SubscriberDefinition definition;
    private PlatformCallback callback;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private ExecutorService executorService;
    private Socket clientSocket;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean connectionEstablished = new AtomicBoolean(false);
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60; // Maximum backoff of 1 minute
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 5; // Start with 5 seconds

    public TcpRateSubscriber() {
        // Default constructor for dynamic loading
    }
    
    @Override
    public void initialize(SubscriberDefinition definition, PlatformCallback callback) {
        this.definition = definition;
        this.callback = callback;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "tcp-subscriber-" + definition.getName());
            t.setDaemon(true);
            return t;
        });
        logger.info("TCP Abonesi {} semboller için başlatıldı: {}", definition.getName(), definition.getSubscribedSymbols());
    }

    @Override
    public void startSubscription() {
        if (active.compareAndSet(false, true)) {
            LoggingHelper.logStartup(logger, "TCP Abonesi " + definition.getName());
            executorService.submit(this::connectAndListen);
            callback.onStatusChange(definition.getName(), "Abonelik başlatıldı");
        } else {
            LoggingHelper.logWarning(logger, "TCP Abonesi " + definition.getName(), "Zaten aktif, yeniden başlatılamaz");
        }
    }

    private void connectAndListen() {
        reconnectAttempts.set(0);
        while (active.get()) {
            try {
                // Calculate backoff delay based on reconnect attempts
                int currentAttempt = reconnectAttempts.get();
                int delaySeconds = calculateReconnectDelay(currentAttempt);
                
                if (currentAttempt > 0) {
                    logger.info("TCP Abonesi {}: Yeniden bağlanma denemesi {} - {} saniye bekleniyor", 
                               definition.getName(), currentAttempt, delaySeconds);
                    callback.onStatusChange(definition.getName(), "Yeniden bağlanma denemesi " + currentAttempt);
                    
                    // Wait before attempting to reconnect
                    try {
                        TimeUnit.SECONDS.sleep(delaySeconds);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warn("TCP Abonesi {}: Yeniden bağlanma beklemesi kesildi", definition.getName());
                        break;
                    }
                    
                    if (!active.get()) {
                        logger.info("TCP Abonesi {}: Abonelik durduruluyor, yeniden bağlanma iptal edildi", definition.getName());
                        break;
                    }
                }
                
                // Get connection parameters, with potential overrides from additionalProperties
                String host = getConnectionHost();
                int port = getConnectionPort();
                int connectionTimeout = getConnectionTimeout();
                
                logger.info("TCP Abonesi {}: Bağlanılıyor {}:{} (timeout: {}ms)", 
                           definition.getName(), host, port, connectionTimeout);
                
                // Create and configure socket
                clientSocket = new Socket();
                clientSocket.connect(new java.net.InetSocketAddress(host, port), connectionTimeout);
                clientSocket.setSoTimeout(getReadTimeout());
                
                // Set up readers/writers
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                
                // Update status
                connectionEstablished.set(true);
                reconnectAttempts.set(0); // Reset attempts counter on successful connection
                callback.onStatusChange(definition.getName(), "Bağlantı başarılı: " + host + ":" + port);
                logger.info("TCP Abonesi {}: Bağlantı başarılı {}:{}", definition.getName(), host, port);

                // Subscribe to symbols
                for (String symbol : definition.getSubscribedSymbols()) {
                    String subscribeMessage = formatSubscribeMessage(symbol);
                    logger.info("TCP Abonesi {}: Abonelik gönderiliyor: {}", definition.getName(), subscribeMessage);
                    out.println(subscribeMessage);
                    // Optional: Wait for subscription confirmation if protocol supports it
                }

                // Main read loop
                String inputLine;
                while (active.get() && (inputLine = in.readLine()) != null) {
                    logger.debug("TCP Abonesi {}: Ham veri alındı: {}", definition.getName(), inputLine);
                    try {
                        processRateData(inputLine);
                    } catch (Exception e) {
                        logger.error("TCP Abonesi {}: Kur verisi ayrıştırılırken hata '{}'", definition.getName(), inputLine, e);
                        callback.onError(definition.getName(), "Kur verisi ayrıştırma hatası: " + e.getMessage(), e);
                    }
                }
                
                logger.info("TCP Abonesi {}: Soket bağlantısı kapandı", definition.getName());
                
            } catch (SocketTimeoutException e) {
                handleConnectionError("Soket zaman aşımı", e);
            } catch (IOException e) {
                if (active.get()) {
                    handleConnectionError("Bağlantı hatası", e);
                } else {
                    logger.info("TCP Abonesi {}: Abone durdurulduğu için soket kapatıldı", definition.getName());
                }
            } finally {
                connectionEstablished.set(false);
                closeSocket();
                reconnectAttempts.incrementAndGet();
            }
        }
        logger.info("TCP Abonesi {} dinleyici iş parçacığı durduruldu", definition.getName());
        callback.onStatusChange(definition.getName(), "Abonelik durduruldu");
    }
    
    private void handleConnectionError(String errorType, Exception e) {
        if (active.get()) {
            LoggingHelper.logError(logger, "TCP Abonesi " + definition.getName(), errorType, e);
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
            return MAX_RECONNECT_DELAY_SECONDS;
        }
        
        // Exponential backoff with jitter: min(maxDelay, initialDelay * 2^attempt) + random(0, 1000ms)
        int exponentialDelay = Math.min(
            MAX_RECONNECT_DELAY_SECONDS,
            INITIAL_RECONNECT_DELAY_SECONDS * (int)Math.pow(2, Math.min(attempt, 6)) // Cap at 2^6 to avoid overflow
        );
        
        // Add jitter (up to 1 second)
        int jitter = (int)(Math.random() * 1000) / 1000;
        return exponentialDelay + jitter;
    }
    
    private String formatSubscribeMessage(String symbol) {
        // Default format is "subscribe|SYMBOL"
        // Check if there's a custom format in additionalProperties
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("subscribeMessageFormat")) {
            String format = (String)definition.getAdditionalProperties().get("subscribeMessageFormat");
            return format.replace("{symbol}", symbol);
        }
        return "subscribe|" + symbol;
    }
    
    private String getConnectionHost() {
        return definition.getHost();
    }
    
    private int getConnectionPort() {
        return definition.getPort() != null ? definition.getPort() : 8080; // Default port
    }
    
    private int getConnectionTimeout() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("connectionTimeoutMs")) {
            return ((Number)definition.getAdditionalProperties().get("connectionTimeoutMs")).intValue();
        }
        return 5000; // Default 5 seconds
    }
    
    private int getReadTimeout() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("readTimeoutMs")) {
            return ((Number)definition.getAdditionalProperties().get("readTimeoutMs")).intValue();
        }
        return 10000; // Default 10 seconds
    }
    
    private int getMaxRetryAttempts() {
        if (definition.getAdditionalProperties() != null && 
            definition.getAdditionalProperties().containsKey("retryAttempts")) {
            return ((Number)definition.getAdditionalProperties().get("retryAttempts")).intValue();
        }
        return 0; // Unlimited by default
    }
    
    private void processRateData(String data) {
        Rate rate = parseRate(data);
        if (rate != null) {
            callback.onRateUpdate(rate);
        }
    }
    
    // PF1_USDTRY|22:number:34.401355|25:number:35.401355|5:timestamp:2024-12-15T11:31:34.509Z
    private Rate parseRate(String data) {
        // Example parsing, needs to be robust
        String[] parts = data.split("\\|");
        if (parts.length < 4) {
            logger.warn("TCP Abonesi {}: Geçersiz veri formatı: {}", definition.getName(), data);
            return null;
        }
        String symbol = parts[0];
        if (!definition.getSubscribedSymbols().contains(symbol)) {
             logger.trace("TCP Abonesi {}: Abone olunmayan sembol için kur yoksayılıyor {}", definition.getName(), symbol);
             return null;
        }

        try {
            BigDecimal bid = new BigDecimal(parts[1].split(":")[2]);
            BigDecimal ask = new BigDecimal(parts[2].split(":")[2]);
            // Timestamp from TCP provider is expected to be ISO_INSTANT (e.g., "2024-12-15T11:31:34.509Z")
            Instant timestamp = Instant.parse(parts[3].split(":")[2]); 

            RateFields rateFields = new RateFields(bid, ask, timestamp);
            return new Rate(symbol, definition.getName(), rateFields, RateStatus.ACTIVE);
        } catch (NumberFormatException | DateTimeParseException | ArrayIndexOutOfBoundsException e) {
            logger.error("TCP Abonesi {}: '{}' içinden kur bileşenleri ayrıştırılamadı", definition.getName(), data, e);
            return null;
        }
    }

    private void closeSocket() {
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                clientSocket.close();
                logger.info("TCP Abonesi {}: Soket kapatıldı", definition.getName());
            } catch (IOException e) {
                logger.error("TCP Abonesi {}: Soket kapatılırken hata", definition.getName(), e);
            }
        }
        clientSocket = null;
    }

    @Override
    public void stopSubscription() {
        if (active.compareAndSet(true, false)) {
            LoggingHelper.logShutdown(logger, "TCP Abonesi " + definition.getName());
            closeSocket(); // Close socket to interrupt blocking read
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
            logger.info("TCP Abonesi {} durduruldu", definition.getName());
        } else {
            logger.warn("TCP Abonesi {} zaten durdurulmuş veya başlatılmamış", definition.getName());
        }
    }

    @Override
    public String getPlatformName() {
        return definition != null ? definition.getName() : "Başlatılmamış TCP Abonesi";
    }

    @Override
    public List<String> getSubscribedSymbols() {
        return definition != null ? definition.getSubscribedSymbols() : List.of();
    }

    @Override
    public boolean isActive() {
        return active.get();
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
