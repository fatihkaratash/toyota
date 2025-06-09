package com.toyota.mainapp.subscriber.impl;

import com.toyota.mainapp.coordinator.callback.PlatformCallback;
import com.toyota.mainapp.dto.model.ProviderRateDto;
import com.toyota.mainapp.subscriber.api.PlatformSubscriber;
import com.toyota.mainapp.util.SubscriberUtils;
import com.toyota.mainapp.dto.config.SubscriberConfigDto;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private String host;
    private int port;
    private int timeout;
    private int retries;
    private String[] symbols;
    private String username;
    private String password;

    private final Set<String> dynamicSubscriptions = ConcurrentHashMap.newKeySet();

    @Override
    public void init(SubscriberConfigDto config, PlatformCallback callback) {
        this.providerName = config.getName();
        this.callback = callback;

        // Load config
        Map<String, Object> connConfig = config.getConnectionConfig();
        this.host = SubscriberUtils.getConfigValue(connConfig, "host", "tcp-rate-provider");
        this.port = SubscriberUtils.getConfigValue(connConfig, "port", 8081);
        this.timeout = getEnvInt("CONNECTION_TIMEOUT_SECONDS", 3) * 1000;
        this.retries = getEnvInt("MAX_RETRY_ATTEMPTS", 3);
        this.symbols = SubscriberUtils.getSymbols(connConfig, this.providerName);
        this.username = SubscriberUtils.getConfigValue(connConfig, "username", System.getenv("CLIENT_TCP_USERNAME"));
        this.password = SubscriberUtils.getConfigValue(connConfig, "password", System.getenv("CLIENT_TCP_PASSWORD"));

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("TCP username and password required");
        }

        log.info("[{}] TCP Subscriber initialized - host: {}, port: {}, symbols: {}", 
                providerName, host, port, symbols.length);
    }

    @Override
    public void connect() {
        int attempt = 0;
        long delay = 100;

        while (attempt < retries && !connected.get()) {
            try {
                attempt++;
                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), timeout);
                socket.setSoTimeout(5000);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);

                connected.set(true);
                
                if (performAuthentication()) {
                    callback.onProviderConnectionStatus(providerName, true, "TCP connected and authenticated");
                    return;
                } else {
                    closeResources();
                    connected.set(false);
                }

            } catch (IOException e) {
                if (attempt < retries) {
                    try {
                        Thread.sleep(delay);
                        delay *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("[{}] TCP connection failed after {} attempts", providerName, retries);
        callback.onProviderConnectionStatus(providerName, false, "TCP connection failed");
    }

    private boolean performAuthentication() throws IOException {
        writer.println("AUTH|" + username + "|" + password);
        writer.flush();

        String response = reader.readLine();
        boolean success = "OK|Authenticated".equals(response);
        
        if (!success) {
            log.error("[{}] TCP authentication failed: {}", providerName, response);
        }
        
        return success;
    }

    @Override
    public void disconnect() {
        running.set(false);
        closeResources();
        connected.set(false);
        callback.onProviderConnectionStatus(providerName, false, "TCP disconnected");
    }

    @Override
    public void startMainLoop() {
        if (!connected.get()) {
            log.warn("[{}] Cannot start main loop - not connected", providerName);
            return;
        }

        if (running.compareAndSet(false, true)) {
            // Subscribe to configured symbols
            for (String symbol : symbols) {
                writer.println("subscribe|" + symbol.toUpperCase());
                writer.flush();
            }

            Thread thread = new Thread(() -> {
                while (running.get()) {
                    try {
                        if (!connected.get()) {
                            reconnect();
                            continue;
                        }

                        String line = reader.readLine();
                        if (line == null) {
                            handleConnectionLost();
                            continue;
                        }

                        processLine(line);
                        
                    } catch (IOException e) {
                        if (running.get()) {
                            log.error("[{}] TCP read error: {}", providerName, e.getMessage());
                            handleConnectionLost();
                        }
                    } catch (Exception e) {
                        log.error("[{}] Processing error: {}", providerName, e.getMessage());
                        callback.onProviderError(providerName, "TCP processing error", e);
                    }

                    if (!connected.get() && running.get()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                closeResources();
            });

            thread.setName("TCP-" + providerName);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void reconnect() {
        if (!running.get()) return;

        closeResources();

        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(timeout);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            connected.set(true);

            if (performAuthentication()) {
                callback.onProviderConnectionStatus(providerName, true, "TCP reconnected");

                // Re-subscribe to all symbols
                for (String symbol : symbols) {
                    writer.println("subscribe|" + symbol.toUpperCase());
                    writer.flush();
                }
                for (String symbol : dynamicSubscriptions) {
                    writer.println("subscribe|" + symbol);
                    writer.flush();
                }
            } else {
                connected.set(false);
            }
        } catch (IOException e) {
            log.error("[{}] Reconnection failed: {}", providerName, e.getMessage());
            connected.set(false);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleConnectionLost() {
        connected.set(false);
        callback.onProviderConnectionStatus(providerName, false, "TCP connection lost");
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
            String[] parts = line.split("\\|");
            if (parts.length < 3) return;

            String symbol = parts[0].trim();
            double bid = 0.0;
            double ask = 0.0;

            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                if (part.contains("22:number:")) {
                    bid = Double.parseDouble(part.substring(part.lastIndexOf(":") + 1));
                } else if (part.contains("25:number:")) {
                    ask = Double.parseDouble(part.substring(part.lastIndexOf(":") + 1));
                }
            }

            ProviderRateDto rate = new ProviderRateDto();
            rate.setSymbol(symbol);
            rate.setBid(String.valueOf(bid));
            rate.setAsk(String.valueOf(ask));
            rate.setProviderName(providerName);
            rate.setTimestamp(System.currentTimeMillis());

            callback.onRateAvailable(providerName, rate);
        } catch (Exception e) {
            log.warn("[{}] Failed to process line: {}", providerName, e.getMessage());
        }
    }

    private void closeResources() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            log.error("[{}] Error closing resources: {}", providerName, e.getMessage());
        }
    }

    public boolean addSymbolSubscription(String symbol) {
        if (!isConnected()) return false;
        
        String upperSymbol = symbol.toUpperCase();
        if (dynamicSubscriptions.contains(upperSymbol)) return true;
        
        try {
            writer.println("subscribe|" + upperSymbol);
            writer.flush();
            dynamicSubscriptions.add(upperSymbol);
            return true;
        } catch (Exception e) {
            log.error("[{}] Failed to subscribe to {}: {}", providerName, upperSymbol, e.getMessage());
            return false;
        }
    }

    public boolean removeSymbolSubscription(String symbol) {
        String upperSymbol = symbol.toUpperCase();
        try {
            if (writer != null) {
                writer.println("unsubscribe|" + upperSymbol);
                writer.flush();
            }
            dynamicSubscriptions.remove(upperSymbol);
            return true;
        } catch (Exception e) {
            log.error("[{}] Failed to unsubscribe from {}: {}", providerName, upperSymbol, e.getMessage());
            return false;
        }
    }

    public Set<String> getActiveSubscriptions() {
        Set<String> allSubscriptions = new HashSet<>(Arrays.asList(symbols));
        allSubscriptions.addAll(dynamicSubscriptions);
        return allSubscriptions;
    }

    private int getEnvInt(String envName, int defaultValue) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }
}
