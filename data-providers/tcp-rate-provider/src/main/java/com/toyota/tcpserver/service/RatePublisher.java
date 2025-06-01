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
            boolean added = listeners.add(listener);
            log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, null,
                    "RateUpdateListener eklendi: " + listener.toString() + " (Eklendi mi: " + added + "). Toplam dinleyici sayısı: " + listeners.size());
        }
    }

    public void removeListener(RateUpdateListener listener) {
        if (listener != null) {
            boolean removed = listeners.remove(listener);
            log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, null,
                    "RateUpdateListener kaldırıldı: " + listener.toString() + " (Kaldırıldı mı: " + removed + "). Kalan dinleyici sayısı: " + listeners.size());
        }
    }

    public void start() {
        if (running) {
            log.warn(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, null, 
                    "RatePublisher zaten çalışıyor.");
            return;
        }
        if (currentRates.isEmpty()) {
            log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, null, 
                    "RatePublisher başlatılamıyor: Başlangıç kurları yapılandırılmamış veya yüklenmemiş.");
            return;
        }
        running = true;
        long publishIntervalMs = configurationReader.getPublishIntervalMs();
        scheduler.scheduleAtFixedRate(this::publishRates, 0, publishIntervalMs, TimeUnit.MILLISECONDS);
        log.info(LoggingHelper.OPERATION_START, LoggingHelper.PLATFORM_PF1, null, 
                "RatePublisher başlatıldı. Her " + publishIntervalMs + " ms'de bir kurlar yayınlanacak.");
    }

    private void publishRates() {
        if (!running) return;
        try {
            log.debug(LoggingHelper.OPERATION_UPDATE, LoggingHelper.PLATFORM_PF1, null, 
                    "Kur yayınlama döngüsü başladı.");
            for (String pairName : currentRates.keySet()) {
                Rate originalRate = currentRates.get(pairName);
                if (originalRate == null) { // Düzgün başlatılmış ConcurrentHashMap'te olmamalı
                    log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, pairName, 
                            "Orijinal kur null, dalgalanma atlanıyor.");
                    continue;
                }
                Rate fluctuatedRate = simulator.fluctuateRate(originalRate);
                currentRates.put(pairName, fluctuatedRate); // yeni kur ile güncelle

                String rateInfo = String.format("BID:%.5f ASK:%.5f", fluctuatedRate.getBid(), fluctuatedRate.getAsk());       
                // İlgili dinleyicilere yayın yap
                notifyListeners(fluctuatedRate);
                
                log.trace(LoggingHelper.OPERATION_UPDATE, LoggingHelper.PLATFORM_PF1, pairName, rateInfo,
                        "Kur güncellendi ve yayınlandı");
            }
            log.debug(LoggingHelper.OPERATION_UPDATE, LoggingHelper.PLATFORM_PF1, null, 
                    "Kur yayınlama döngüsü tamamlandı.");
        } catch (Exception e) {
            log.error(LoggingHelper.PLATFORM_PF1, null, 
                    "Kur yayınlama döngüsü sırasında hata", e);
        }
    }

    private void notifyListeners(Rate rate) {
        String pairName = rate.getPairName();
        int notifiedCount = 0;
        
        log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, pairName,
                "notifyListeners: '" + pairName + "' için dinleyicilere bildirim yapılıyor. Toplam dinleyici: " + listeners.size());

        if (listeners.isEmpty()) {
            log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, pairName,
                    "notifyListeners: '" + pairName + "' için aktif dinleyici bulunmuyor.");
            return;
        }

        for (RateUpdateListener listener : listeners) {
            try {
                log.trace(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, pairName,
                        "notifyListeners: Dinleyici kontrol ediliyor: " + listener.toString() + " '" + pairName + "' için.", null);
                if (listener.isSubscribedTo(pairName)) {
                    log.debug(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, pairName,
                            "notifyListeners: Dinleyici " + listener.toString() + " '" + pairName + "' kuruna abone. Güncelleme gönderiliyor.");
                    listener.onRateUpdate(rate);
                    notifiedCount++;
                } else {
                    log.trace(LoggingHelper.OPERATION_INFO, LoggingHelper.PLATFORM_PF1, pairName,
                            "notifyListeners: Dinleyici " + listener.toString() + " '" + pairName + "' kuruna abone DEĞİL.", null);
                }
            } catch (Exception e) {
                log.error(LoggingHelper.PLATFORM_PF1, pairName,
                        "Dinleyiciye bildirim gönderilirken hata oluştu: " + e.getMessage(), e);
            }
        }
        
        log.trace(LoggingHelper.OPERATION_UPDATE, LoggingHelper.PLATFORM_PF1, pairName, null,
                pairName + " kuruna abone olan " + notifiedCount + " dinleyiciye bildirim gönderildi");
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
