package com.toyota.provider.tcp;

import com.toyota.provider.common.Doviz;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Socket clientSocket;
    private final TcpDovizService dovizService;
    private final Set<String> subscriptions = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private PrintWriter out;

    public ClientHandler(Socket socket, TcpDovizService dovizService) {
        this.clientSocket = socket;
        this.dovizService = dovizService;
    }

    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;
            while (running.get() && (inputLine = in.readLine()) != null) {
                logger.debug("Received from client {}: {}", clientSocket.getRemoteSocketAddress(), inputLine);
                processCommand(inputLine);
            }
        } catch (IOException e) {
            if (running.get()) { // Avoid logging error if closed intentionally
                logger.error("Error handling client {}: {}", clientSocket.getRemoteSocketAddress(), e.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split("\\|");
        if (parts.length == 0) return;

        String action = parts[0].trim().toLowerCase();
        switch (action) {
            case "subscribe":
                if (parts.length > 1) {
                    String rateName = parts[1].trim();
                    if (dovizService.isValidRate(rateName)) {
                        subscriptions.add(rateName);
                        out.println("Subscribed to " + rateName);
                        logger.info("Client {} subscribed to {}", clientSocket.getRemoteSocketAddress(), rateName);
                        sendRateUpdate(rateName); // Send current rate immediately
                    } else {
                        out.println("Error: Invalid rate name " + rateName);
                    }
                }
                break;
            case "unsubscribe":
                if (parts.length > 1) {
                    String rateName = parts[1].trim();
                    subscriptions.remove(rateName);
                    out.println("Unsubscribed from " + rateName);
                    logger.info("Client {} unsubscribed from {}", clientSocket.getRemoteSocketAddress(), rateName);
                }
                break;
            default:
                out.println("Error: Unknown command " + action);
        }
    }

    public void sendRateUpdate(String rateName) {
        if (subscriptions.contains(rateName) && out != null) {
            Doviz doviz = dovizService.getLatestDoviz(rateName);
            if (doviz != null) {
                // Format: PF1_USDTRY|bid:value|ask:value|timestamp:value
                String message = String.format("%s|bid:%.6f|ask:%.6f|timestamp:%s",
                        doviz.getPair(), doviz.getBid(), doviz.getAsk(), doviz.getTimestamp());
                out.println(message);
            }
        }
    }
    
    public Set<String> getSubscriptions() {
        return subscriptions;
    }

    public void stopHandler() {
        running.set(false);
        closeConnection();
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            logger.info("Client connection closed: {}", clientSocket.getRemoteSocketAddress());
        } catch (IOException e) {
            logger.error("Error closing client socket {}: {}", clientSocket.getRemoteSocketAddress(), e.getMessage());
        }
    }
}
