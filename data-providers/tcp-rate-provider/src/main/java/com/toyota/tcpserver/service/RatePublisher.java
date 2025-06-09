package com.toyota.tcpserver.service;

import com.toyota.tcpserver.config.ConfigurationReader;
import com.toyota.tcpserver.model.Rate;
import com.toyota.tcpserver.event.RateUpdateListener;
import com.toyota.tcpserver.logging.LoggingHelper;
import com.toyota.tcpserver.network.ClientHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class RatePublisher {
    private static final LoggingHelper log = new LoggingHelper(RatePublisher.class);

    private final Map<String, Rate> currentRates;
    private final RateFluctuationSimulator simulator;
    private final ConfigurationReader configurationReader;
    private final ScheduledExecutorService scheduler;
    private final Set<RateUpdateListener> listeners = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;


    public RatePublisher(ConfigurationReader configurationReader, List<ClientHandler> clientHandlers) {
        this.configurationReader = configurationReader;
        this.simulator = new RateFluctuationSimulator(configurationReader);
        
        // configurationReader currentrates
        this.currentRates = new ConcurrentHashMap<>();
        List<Rate> initialRates = configurationReader.getInitialRates();
        if (initialRates == null || initialRates.isEmpty()) {
            log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, null, 
                    "Başlangıç kurları yüklenemedi. RatePublisher düzgün çalışmayabilir.");
        } else {
            for (Rate rate : initialRates) {
                this.currentRates.put(rate.getPairName(), rate.copy()); // Kopyaları sakla
            }
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RatePublisherThread");
            t.setDaemon(true);
            return t;
        });
    }

    public void addListener(RateUpdateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(RateUpdateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void start() {
        if (running) {
            return;
        }
        if (currentRates.isEmpty()) {
            log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, null, 
                    "RatePublisher başlatılamıyor: Başlangıç kurları yüklenmemiş.");
            return;
        }
        running = true;
        
        // Check environment for interval override
        long publishIntervalMs = getEnvironmentInterval();
        
        scheduler.scheduleAtFixedRate(this::publishRates, 0, publishIntervalMs, TimeUnit.MILLISECONDS);
        log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_PF1, null, 
                "RatePublisher başlatıldı. Interval: " + publishIntervalMs + " ms");
    }

    private long getEnvironmentInterval() {
        String envInterval = System.getenv("TCP_PROVIDER_INTERVAL_MS");
        if (envInterval != null && !envInterval.trim().isEmpty()) {
            try {
                long interval = Long.parseLong(envInterval.trim());
                log.info(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, null,
                        "TCP Provider interval environment'tan alındı: " + interval + " ms");
                return interval;
            } catch (NumberFormatException e) {
                log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, null,
                        "Geçersiz TCP_PROVIDER_INTERVAL_MS değeri: " + envInterval + ", varsayılan kullanılıyor");
            }
        }
        return configurationReader.getPublishIntervalMs();
    }

    private void publishRates() {
        if (!running) return;
        try {
            for (String pairName : currentRates.keySet()) {
                Rate originalRate = currentRates.get(pairName);
                if (originalRate == null) continue;
                
                Rate fluctuatedRate = simulator.fluctuateRate(originalRate);
                currentRates.put(pairName, fluctuatedRate);
                notifyListeners(fluctuatedRate);
            }
        } catch (Exception e) {
            log.error(LoggingHelper.PLATFORM_PF1, null, 
                    "Kur yayınlama sırasında hata", e);
        }
    }

    private void notifyListeners(Rate rate) {
        String pairName = rate.getPairName();
        
        for (RateUpdateListener listener : listeners) {
            try {
                if (listener.isSubscribedTo(pairName)) {
                    listener.onRateUpdate(rate);
                }
            } catch (Exception e) {
                log.error(LoggingHelper.PLATFORM_PF1, pairName,
                        "Dinleyici bildiriminde hata: " + e.getMessage(), e);
            }
        }
    }
    
    public boolean isValidRatePair(String rateName) {
        return currentRates.containsKey(rateName);
    }

    public Rate getCurrentRate(String pairName) {
        Rate rate = currentRates.get(pairName);
        return (rate != null) ? rate.copy() : null; // Bir kopya döndür
    }


    public void stop() {
        running = false;
        listeners.clear(); // Tüm dinleyicileri temizle
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, null, 
                        "RatePublisher planlaması düzgün sonlandırılamadı, zorla kapatılıyor.");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info(LoggingHelper.OPERATION_STOP, LoggingHelper.PLATFORM_PF1, null, 
                "RatePublisher durduruldu.");
    }
}
