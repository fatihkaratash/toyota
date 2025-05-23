package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.BaseRateDto;
import com.toyota.mainapp.dto.ProviderRateDto;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.api.SubscriberConfigDto;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TcpRateSubscriber implements PlatformSubscriber {
    private String providerName;
    private PlatformCallback callback;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    
    private String host = "tcp-rate-provider"; // Default host updated to "tcp-rate-provider"
    private int port = 8081; // Default TCP port updated to 8081
    private int timeout = 30000;
    private int retries = 10;
    private String[] symbols = new String[0];

    @Override
    public void init(SubscriberConfigDto config, PlatformCallback callback) {
        this.providerName = config.getName();
        this.callback = callback;
        
        if (config.getConnectionConfig() != null) {
            Map<String, Object> connConfig = config.getConnectionConfig();
            this.host = getString(connConfig, "host", "tcp-rate-provider"); // Default host updated to "tcp-rate-provider"
            this.port = getInt(connConfig, "port", 8081); // Default TCP port updated to 8081
            this.timeout = getInt(connConfig, "connectionTimeoutMs", 30000);
            this.retries = getInt(connConfig, "retryAttempts", 3);
            this.symbols = getSymbols(connConfig);
        }
        
        log.debug("[{}] TCP Subscriber initialized with config: host={}, port={}, timeout={}, retries={}, symbols={}",
                providerName, host, port, timeout, retries, Arrays.toString(symbols));
        log.info("TCP abone başlatıldı: {}", providerName);
    }

    @Override
    public void connect() {
        int attempt = 0;
        Exception lastError = null;
        
        while (attempt < retries && !connected.get()) {
            try {
                attempt++;
                log.info("[{}] TCP bağlantısı deneniyor: {} ({}/{}) to {}:{}", providerName, attempt, retries, host, port);
                
                socket = new Socket(host, port);
                socket.setSoTimeout(timeout);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);
                
                connected.set(true);
                log.info("[{}] TCP bağlantısı başarıyla kuruldu: {}:{}", providerName, host, port);
                callback.onProviderConnectionStatus(providerName, true, "TCP bağlantısı kuruldu");
                return;
                
            } catch (IOException e) {
                lastError = e;
                log.warn("[{}] TCP bağlantı hatası deneme {}/{}: {}:{} - {}", 
                         providerName, attempt, retries, host, port, e.getMessage(), e);
                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    log.warn("[{}] TCP bağlantı denemesi bekleme kesintiye uğradı", providerName, ie);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.error("[{}] TCP bağlantısı {} denemeden sonra kurulamadı: {}:{}", providerName, retries, host, port,
                (lastError != null ? lastError.getMessage() : "Bilinmeyen hata"), lastError);
        callback.onProviderConnectionStatus(providerName, false, 
                "TCP bağlantısı kurulamadı: " + (lastError != null ? lastError.getMessage() : "Bilinmeyen hata"));
    }

    @Override
    public void disconnect() {
        log.info("[{}] TCP bağlantısı kapatılıyor...", providerName);
        closeResources();
        connected.set(false);
        callback.onProviderConnectionStatus(providerName, false, "TCP bağlantısı kapatıldı");
        log.info("[{}] TCP bağlantısı kapatıldı.", providerName);
    }

    @Override
    public void startMainLoop() {
        if (!connected.get()) {
            log.warn("[{}] TCP ana döngüsü başlatılamadı, bağlantı yok.", providerName);
            return;
        }
        if (running.get()) {
            log.warn("[{}] TCP ana döngüsü zaten çalışıyor.", providerName);
            return;
        }
        
        running.set(true);
        log.info("[{}] TCP ana döngüsü başlatılıyor.", providerName);
        
        // Subscribe using proper format
        for (String symbol : symbols) {
            String uppercaseSymbol = symbol.toUpperCase();
            log.debug("[{}] Sending SUBSCRIBE command for symbol: {}", providerName, uppercaseSymbol);
            writer.println("subscribe|" + uppercaseSymbol); 
            writer.flush(); // Ensure data is sent immediately
            log.info("[{}] Sent SUBSCRIBE command for symbol: {}", providerName, uppercaseSymbol);
        }
        
        // Ana döngü
    Thread thread = new Thread(() -> {
        log.info("[{}] TCP ana dinleme thread'i başlatıldı.", providerName);
        while (running.get()) {
            try {
                if (!connected.get()) {
                    log.warn("[{}] TCP bağlantısı yok, yeniden bağlanmaya çalışılıyor...", providerName);
                    reconnect(); 
                    continue;
                }
                
                log.debug("[{}] Veri okunuyor...", providerName);
                String line = reader.readLine();
                log.trace("[{}] Raw line received: {}", providerName, line);

                if (line == null) {
                    handleConnectionLost("Bağlantı kapatıldı (sunucu null gönderdi)");
                    continue;
                }
                
                processLine(line);
            } catch (IOException e) {
                if (running.get()) { // Log error only if still supposed to be running
                    log.error("[{}] TCP okuma hatası: {}", providerName, e.getMessage(), e);
                    handleConnectionLost("Okuma hatası: " + e.getMessage());
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("[{}] TCP işleme sırasında beklenmedik hata: {}", providerName, e.getMessage(), e);
                    callback.onProviderError(providerName, "TCP veri işleme hatası", e);
                }
            }
            
            // Bağlantı koptu ise kısa bir bekleme yap
            if (!connected.get() && running.get()) {
                try {
                    log.debug("[{}] Bağlantı koptu, 1 saniye bekleniyor...", providerName);
                    Thread.sleep(1000); // Aşırı CPU kullanımını önle
                } catch (InterruptedException ie) {
                    log.warn("[{}] Yeniden bağlanma beklemesi kesintiye uğradı", providerName, ie);
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Ana döngü sonlandı, kaynakları temizle
        log.info("[{}] TCP ana dinleme thread'i sonlandırılıyor.", providerName);
        closeResources();
        connected.set(false);
        if (callback != null) { // callback null olabilir eğer init düzgün tamamlanmadıysa
            callback.onProviderConnectionStatus(providerName, false, "TCP bağlantısı kapatıldı (döngü sonu)");
        }
    });
    
    thread.setName("TCP-" + providerName);
    thread.setDaemon(true); // Arka plan thread'i olarak ayarla
    thread.start();
}

// Yeniden bağlanma metodunu ekle -- Reconnec hatasını çözmeye calıstım
private void reconnect() {
    if (!running.get()) {
        log.debug("[{}] Yeniden bağlanma atlandı, ana döngü çalışmıyor.", providerName);
        return;
    }
    
    log.info("[{}] TCP yeniden bağlanılıyor...", providerName);
    closeResources(); // Mevcut kaynakları temizle
    
    try {
        socket = new Socket(host, port);
        socket.setSoTimeout(timeout);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
        
        connected.set(true);
        log.info("[{}] TCP bağlantısı başarıyla yeniden kuruldu: {}:{}", providerName, host, port);
        callback.onProviderConnectionStatus(providerName, true, "TCP bağlantısı yeniden kuruldu");
        
        // Yeniden abone ol
        for (String symbol : symbols) {
            String uppercaseSymbol = symbol.toUpperCase();
            log.debug("[{}] Sending RE-SUBSCRIBE command for symbol: {} after reconnect", providerName, uppercaseSymbol);
            writer.println("subscribe|" + uppercaseSymbol); 
            writer.flush(); // Ensure data is sent immediately
            log.info("[{}] Sent RE-SUBSCRIBE command for symbol: {} after reconnect", providerName, uppercaseSymbol);
        }
    } catch (IOException e) {
        log.error("[{}] TCP yeniden bağlanma hatası: {}:{} - {}", providerName, host, port, e.getMessage(), e);
        connected.set(false);
        
        // Hemen tekrar denemeyi önlemek için kısa bir bekleme
        try {
            log.debug("[{}] Yeniden bağlanma hatası sonrası 3 saniye bekleniyor...", providerName);
            Thread.sleep(3000);
        } catch (InterruptedException ie) {
            log.warn("[{}] Yeniden bağlanma hatası beklemesi kesintiye uğradı", providerName, ie);
            Thread.currentThread().interrupt();
        }
    }
}

// Bağlantı kopması durumunu yönet
private void handleConnectionLost(String reason) {
    log.warn("[{}] TCP bağlantısı koptu: {}", providerName, reason);
    connected.set(false);
    callback.onProviderConnectionStatus(providerName, false, "TCP bağlantısı koptu: " + reason);
    closeResources(); // Ensure resources are closed on handled disconnect
    log.debug("[{}] handleConnectionLost tamamlandı.", providerName);
}

    @Override
    public void stopMainLoop() {
        log.info("[{}] TCP ana döngüsü durduruluyor...", providerName);
        running.set(false);
        closeResources(); // Ensure resources are closed when stopping loop
        log.info("[{}] TCP ana döngüsü durduruldu.", providerName);
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    private void processLine(String line) {
        try {
            // Format from TCP server: PAIR_NAME|22:number:BID_VALUE|25:number:ASK_VALUE|5:timestamp:TIMESTAMP_VALUE
            String[] parts = line.split("\\|");
            if (parts.length < 3) {
                log.warn("[{}] TCP geçersiz format: {}", providerName, line);
                return;
            }

            String symbol = parts[0].trim();
            double bid = 0.0;
            double ask = 0.0;
            
            // Parse bid and ask from specialized fields
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                if (part.contains("22:number:")) {
                    bid = Double.parseDouble(part.substring(part.lastIndexOf(":") + 1));
                } else if (part.contains("25:number:")) {
                    ask = Double.parseDouble(part.substring(part.lastIndexOf(":") + 1));
                }
            }
            
            log.debug("[{}] TCP veri alındı: {} (bid={}, ask={})", providerName, symbol, bid, ask);
            
            ProviderRateDto rate = new ProviderRateDto();
            rate.setSymbol(symbol);
            rate.setBid(String.valueOf(bid));
            rate.setAsk(String.valueOf(ask));
            rate.setProviderName(providerName);
            rate.setTimestamp(System.currentTimeMillis());

            log.debug("[{}] Parsed ProviderRateDto: {}", providerName, rate);
            callback.onRateAvailable(providerName, rate);
        } catch (NumberFormatException e) {
            log.warn("[{}] TCP satırı işlenirken sayı formatı hatası: {} - Satır: {}", providerName, e.getMessage(), line, e);
        } catch (Exception e) {
            log.warn("[{}] TCP satırı işlenemedi: {} - Satır: {}", providerName, e.getMessage(), line, e);
        }
    }
    
    private void closeResources() {
        log.debug("[{}] TCP kaynakları kapatılıyor...", providerName);
        try {
            if (writer != null) {
                writer.close();
                log.debug("[{}] PrintWriter kapatıldı.", providerName);
            }
            if (reader != null) {
                reader.close();
                log.debug("[{}] BufferedReader kapatıldı.", providerName);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                log.debug("[{}] Socket kapatıldı.", providerName);
            }
        } catch (IOException e) {
            log.error("[{}] TCP kaynakları kapatılırken hata: {}", providerName, e.getMessage(), e);
        }
        log.debug("[{}] TCP kaynakları kapatıldı.", providerName);
    }
    
    private String getString(Map<String, Object> config, String key, String defaultVal) {
        if (config == null || !config.containsKey(key)) return defaultVal;
        Object val = config.get(key);
        return (val instanceof String) ? (String)val : defaultVal;
    }
    
    private int getInt(Map<String, Object> config, String key, int defaultVal) {
        if (config == null || !config.containsKey(key)) return defaultVal;
        Object val = config.get(key);
        if (val instanceof Number) return ((Number)val).intValue();
        if (val instanceof String) {
            try {
                return Integer.parseInt((String)val);
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }
    
    private String[] getSymbols(Map<String, Object> config) {
        if (config == null || !config.containsKey("symbols")) {
            log.warn("[{}] Yapılandırmada 'symbols' anahtarı bulunamadı", providerName);
            return new String[0];
        }
        
        Object symbolsObj = config.get("symbols");
        log.debug("[{}] Yapılandırmadan alınan ham symbols nesnesi: {}", 
                providerName, symbolsObj);
        
        List<String> parsedSymbols = new java.util.ArrayList<>();
        
        // Handle List type (most common from JSON)
        if (symbolsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> symbolsList = (List<String>) symbolsObj;
            for (String item : symbolsList) {
                if (item != null) {
                    String[] parts = item.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            parsedSymbols.add(trimmed);
                        }
                    }
                }
            }
        } 
        // Handle simple String
        else if (symbolsObj instanceof String) {
            String symbolsStr = (String) symbolsObj;
            String[] parts = symbolsStr.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    parsedSymbols.add(trimmed);
                }
            }
        }
        // Handle String[] (less common)
        else if (symbolsObj instanceof String[]) {
            for (String s : (String[]) symbolsObj) {
                if (s != null) {
                    String[] parts = s.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            parsedSymbols.add(trimmed);
                        }
                    }
                }
            }
        }
        
        log.info("[{}] Çözümlenen semboller: {}", providerName, parsedSymbols);
        return parsedSymbols.toArray(new String[0]);
    }

    // This method should be added if it doesn't exist:
    public void sendRateStatus(String symbol, BaseRateDto.RateStatusEnum status, String statusMessage) {
        if (callback != null) {
            BaseRateDto statusRate = BaseRateDto.builder()
                .rateType(com.toyota.mainapp.dto.RateType.STATUS)
                .symbol(symbol)
                .providerName(providerName)
                .status(status)
                .statusMessage(statusMessage)
                .timestamp(System.currentTimeMillis())
                .build();
                
            callback.onRateStatus(providerName, statusRate);
        }
    }
}
