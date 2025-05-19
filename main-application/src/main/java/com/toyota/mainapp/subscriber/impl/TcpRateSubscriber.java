package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.model.RateStatus;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpRateSubscriber implements PlatformSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(TcpRateSubscriber.class);

    private SubscriberDefinition definition;
    private PlatformCallback callback;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private ExecutorService executorService;
    private Socket clientSocket;

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
            executorService.submit(this::connectAndListen);
            logger.info("TCP Abonesi {} başlatıldı.", definition.getName());
            callback.onStatusChange(definition.getName(), "Abonelik başlatıldı.");
        } else {
            logger.warn("TCP Abonesi {} zaten aktif.", definition.getName());
        }
    }

    private void connectAndListen() {
        while (active.get()) {
            try {
                logger.info("TCP Abonesi {}: Bağlanılıyor {}:{}", definition.getName(), definition.getHost(), definition.getPort());
                clientSocket = new Socket(definition.getHost(), definition.getPort());
                clientSocket.setSoTimeout(10000); // 10 seconds read timeout
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                callback.onStatusChange(definition.getName(), definition.getHost() + ":" + definition.getPort() + " adresine bağlanıldı");

                // Subscribe to symbols
                for (String symbol : definition.getSubscribedSymbols()) {
                    String subscribeMessage = "subscribe|" + symbol;
                    logger.info("TCP Abonesi {}: Abonelik gönderiliyor: {}", definition.getName(), subscribeMessage);
                    out.println(subscribeMessage);
                    // Optionally, wait for a confirmation message if the protocol defines one
                    // String response = in.readLine(); // Example: "Subscribed to PF1_USDTRY"
                    // logger.info("TCP Abonesi {}: Abonelik yanıtı alındı: {}", definition.getName(), response);
                }

                String inputLine;
                while (active.get() && (inputLine = in.readLine()) != null) {
                    logger.debug("TCP Abonesi {}: Ham veri alındı: {}", definition.getName(), inputLine);
                    try {
                        Rate rate = parseRate(inputLine);
                        if (rate != null) {
                            callback.onRateUpdate(rate);
                        }
                    } catch (Exception e) {
                        logger.error("TCP Abonesi {}: Kur verisi ayrıştırılırken hata '{}'", definition.getName(), inputLine, e);
                        callback.onError(definition.getName(), "Kur verisi ayrıştırma hatası: " + e.getMessage(), e);
                    }
                }
            } catch (SocketTimeoutException e) {
                logger.warn("TCP Abonesi {}: Soket zaman aşımı. Yeniden bağlanmaya çalışılıyor.", definition.getName());
                callback.onStatusChange(definition.getName(), "Soket zaman aşımı. Yeniden bağlanılıyor...");
            } catch (IOException e) {
                if (active.get()) {
                    logger.error("TCP Abonesi {}: Bağlantı hatası. Yeniden bağlanmaya çalışılıyor.", definition.getName(), e);
                    callback.onError(definition.getName(), "Bağlantı hatası: " + e.getMessage(), e);
                    callback.onStatusChange(definition.getName(), "Bağlantı hatası. Yeniden bağlanılıyor...");
                } else {
                    logger.info("TCP Abonesi {}: Abone durdurulduğu için soket kapatıldı.", definition.getName());
                }
            } finally {
                closeSocket();
                if (active.get()) {
                    try {
                        Thread.sleep(5000); // Wait 5 seconds before retrying connection
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warn("TCP Abonesi {}: Yeniden bağlanma beklemesi kesildi.", definition.getName());
                        active.set(false); // Stop if interrupted
                    }
                }
            }
        }
        logger.info("TCP Abonesi {} dinleyici iş parçacığı durduruldu.", definition.getName());
        callback.onStatusChange(definition.getName(), "Abonelik durduruldu.");
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
                logger.info("TCP Abonesi {}: Soket kapatıldı.", definition.getName());
            } catch (IOException e) {
                logger.error("TCP Abonesi {}: Soket kapatılırken hata.", definition.getName(), e);
            }
        }
        clientSocket = null;
    }

    @Override
    public void stopSubscription() {
        if (active.compareAndSet(true, false)) {
            logger.info("TCP Abonesi {} durduruluyor...", definition.getName());
            closeSocket(); // Close socket to interrupt blocking read
            if (executorService != null) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            logger.info("TCP Abonesi {} durduruldu.", definition.getName());
        } else {
            logger.warn("TCP Abonesi {} zaten durdurulmuş veya başlatılmamış.", definition.getName());
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
}
