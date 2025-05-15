package com.toyota.tcpserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class RatePublisher {
    private static final Logger logger = LogManager.getLogger(RatePublisher.class);

    private final Map<String, Rate> currentRates; // Parite adı -> Kur haritalama
    private final RateFluctuationSimulator simulator;
    private final ConfigurationReader configurationReader;
    private final ScheduledExecutorService scheduler;
    private final List<ClientHandler> clientHandlers; // Aktif istemci işleyicilerinin paylaşılan listesi
    private volatile boolean running = false;


    public RatePublisher(ConfigurationReader configurationReader, List<ClientHandler> clientHandlers) {
        this.configurationReader = configurationReader;
        this.clientHandlers = clientHandlers; // Bu liste TcpServer tarafından yönetilir
        this.simulator = new RateFluctuationSimulator(configurationReader);
        
        // configurationReader'dan derin kopyalarla currentRates'i başlat
        this.currentRates = new ConcurrentHashMap<>();
        List<Rate> initialRates = configurationReader.getInitialRates();
        if (initialRates == null || initialRates.isEmpty()) {
            logger.warn("Başlangıç kurları yüklenemedi. RatePublisher düzgün çalışmayabilir.");
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

    public void start() {
        if (running) {
            logger.warn("RatePublisher zaten çalışıyor.");
            return;
        }
        if (currentRates.isEmpty()) {
            logger.warn("RatePublisher başlatılamıyor: Başlangıç kurları yapılandırılmamış veya yüklenmemiş.");
            return;
        }
        running = true;
        long publishIntervalMs = configurationReader.getPublishIntervalMs();
        scheduler.scheduleAtFixedRate(this::publishRates, 0, publishIntervalMs, TimeUnit.MILLISECONDS);
        logger.info("RatePublisher başlatıldı. Her {} ms'de bir kurlar yayınlanacak.", publishIntervalMs);
    }

    private void publishRates() {
        if (!running) return;
        try {
            logger.debug("Kur yayınlama döngüsü başladı.");
            for (String pairName : currentRates.keySet()) {
                Rate originalRate = currentRates.get(pairName);
                if (originalRate == null) { // Düzgün başlatılmış ConcurrentHashMap'te olmamalı
                    logger.warn("{} için orijinal kur null, dalgalanma atlanıyor.", pairName);
                    continue;
                }
                Rate fluctuatedRate = simulator.fluctuateRate(originalRate);
                currentRates.put(pairName, fluctuatedRate); // Dalgalanmış yeni kur ile güncelle

                // İlgili istemcilere yayın yap
                // ConcurrentModificationException'dan kaçınmak için clientHandlers'ın bir anlık görüntüsü üzerinde dolaş
                // clientHandlers listesi başka bir thread tarafından değiştirilirse (örn. TcpServer)
                List<ClientHandler> snapshot;
                synchronized(clientHandlers) { // Güvenli kopyalamayı sağla
                    snapshot = List.copyOf(clientHandlers);
                }

                for (ClientHandler handler : snapshot) {
                    if (handler.isRunning() && handler.isSubscribedTo(pairName)) {
                        handler.sendRateUpdate(fluctuatedRate);
                    }
                }
            }
            logger.debug("Kur yayınlama döngüsü tamamlandı.");
        } catch (Exception e) {
            logger.error("Kur yayınlama döngüsü sırasında hata", e);
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
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warn("RatePublisher planlaması düzgün sonlandırılamadı, zorla kapatılıyor.");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("RatePublisher durduruldu.");
    }
}
