package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.ProviderRateDto;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.subscriber.api.SubscriberConfigDto;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;
import java.time.Instant;
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
            //this.host = getString(connConfig, "host", "localhost"); // Default host updated to "tcp-rate-provider"
            this.port = getInt(connConfig, "port", 8081); // Default TCP port updated to 8081
            this.timeout = getInt(connConfig, "connectionTimeoutMs", 30000);
            this.retries = getInt(connConfig, "retryAttempts", 3);
            this.symbols = getSymbols(connConfig);
        }
        
        log.info("TCP abone başlatıldı: {}", providerName);
    }

    @Override
    public void connect() {
        int attempt = 0;
        Exception lastError = null;
        
        while (attempt < retries && !connected.get()) {
            try {
                attempt++;
                log.info("TCP bağlantısı deneniyor: {} ({}/{})", providerName, attempt, retries);
                
                socket = new Socket(host, port);
                socket.setSoTimeout(timeout);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);
                
                connected.set(true);
                callback.onProviderConnectionStatus(providerName, true, "TCP bağlantısı kuruldu");
                return;
                
            } catch (IOException e) {
                lastError = e;
                log.error("TCP bağlantı hatası: {}", e.getMessage());
                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        callback.onProviderConnectionStatus(providerName, false, 
                "TCP bağlantısı kurulamadı: " + (lastError != null ? lastError.getMessage() : "Bilinmeyen hata"));
    }

    @Override
    public void disconnect() {
        closeResources();
        connected.set(false);
        callback.onProviderConnectionStatus(providerName, false, "TCP bağlantısı kapatıldı");
    }

    @Override
    public void startMainLoop() {
        if (!connected.get() || running.get()) return;
        
        running.set(true);
        
        // Subscribe using proper format
        for (String symbol : symbols) {
            writer.println("subscribe|" + symbol); //hatalıydı format clienttan veri cekemiyordu
            log.info("TCP sembolüne abone olundu: {}", symbol);
        }
        
        // Ana döngü
    Thread thread = new Thread(() -> {
        while (running.get()) {
            try {
                if (!connected.get()) {
                    reconnect(); // Yeni metod: Yeniden bağlanma girişimi
                    continue;
                }
                
                String line = reader.readLine();
                if (line == null) {
                    handleConnectionLost("Bağlantı kapatıldı (null)");
                    continue;
                }
                
                processLine(line);
            } catch (IOException e) {
                handleConnectionLost("Okuma hatası: " + e.getMessage());
            } catch (Exception e) {
                if (running.get()) {
                    log.error("TCP işleme hatası: {}", e.getMessage());
                    callback.onProviderError(providerName, "TCP veri işleme hatası", e);
                }
            }
            
            // Bağlantı koptu ise kısa bir bekleme yap
            if (!connected.get() && running.get()) {
                try {
                    Thread.sleep(1000); // Aşırı CPU kullanımını önle
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Ana döngü sonlandı, kaynakları temizle
        closeResources();
        connected.set(false);
        callback.onProviderConnectionStatus(providerName, false, "TCP bağlantısı kapatıldı");
    });
    
    thread.setName("TCP-" + providerName);
    thread.setDaemon(true); // Arka plan thread'i olarak ayarla
    thread.start();
}

// Yeniden bağlanma metodunu ekle -- Reconnec hatasını çözmeye calıstım
private void reconnect() {
    if (!running.get()) return;
    
    closeResources(); // Mevcut kaynakları temizle
    
    try {
        log.info("TCP yeniden bağlanılıyor: {}", providerName);
        socket = new Socket(host, port);
        socket.setSoTimeout(timeout);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
        
        connected.set(true);
        callback.onProviderConnectionStatus(providerName, true, "TCP bağlantısı yeniden kuruldu");
        
        // Yeniden abone ol
        for (String symbol : symbols) {
            writer.println("subscribe|" + symbol);
            log.info("TCP sembolüne yeniden abone olundu: {}", symbol);
        }
    } catch (IOException e) {
        log.error("TCP yeniden bağlanma hatası: {}", e.getMessage());
        connected.set(false);
        
        // Hemen tekrar denemeyi önlemek için kısa bir bekleme
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

// Bağlantı kopması durumunu yönet
private void handleConnectionLost(String reason) {
    log.warn("TCP bağlantısı koptu: {} - {}", providerName, reason);
    connected.set(false);
    callback.onProviderConnectionStatus(providerName, false, "TCP bağlantısı koptu: " + reason);
    closeResources();
}

    @Override
    public void stopMainLoop() {
        running.set(false);
        closeResources();
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
                log.warn("TCP geçersiz format: {}", line);
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
            
            log.debug("TCP veri alındı: {} (bid={}, ask={})", symbol, bid, ask);
            
            ProviderRateDto rate = new ProviderRateDto();
            rate.setSymbol(symbol);
            rate.setBid(String.valueOf(bid));
            rate.setAsk(String.valueOf(ask));
            rate.setProviderName(providerName);
            rate.setTimestamp(System.currentTimeMillis());

            callback.onRateAvailable(providerName, rate);
        } catch (Exception e) {
            log.warn("TCP satır işlenemedi: {}", line, e);
        }
    }
    
    private void closeResources() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            log.error("Kapatma sırasında hata: {}", e.getMessage());
        }
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
        if (config == null || !config.containsKey("symbols")) return new String[0];
        
        Object symbols = config.get("symbols");
        if (symbols instanceof String[]) {
            return (String[])symbols;
        } else if (symbols instanceof String) {
            return ((String)symbols).split(",");
        }
        
        return new String[0];
    }
}
