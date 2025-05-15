package com.toyota.provider.tcp;

import com.toyota.provider.config.ApplicationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TcpServer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

    private final int port;
    private final TcpDovizService dovizService;
    private final ApplicationProperties applicationProperties;

    private ServerSocket serverSocket;
    private final ExecutorService clientExecutorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService rateUpdateScheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<ClientHandler> clientHandlers = new ArrayList<>();
    private volatile boolean running = true;


    public TcpServer(TcpDovizService dovizService, ApplicationProperties applicationProperties) {
        this.dovizService = dovizService;
        this.applicationProperties = applicationProperties;
        this.port = applicationProperties.getTcp().getPort();
    }

    @PostConstruct
    public void startServer() {
        new Thread(this).start();
        rateUpdateScheduler.scheduleAtFixedRate(this::broadcastUpdates,
                0, applicationProperties.getTcp().getPublishIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            logger.info("TCP Server started on port: {}", port);
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("New client connected: {}", clientSocket.getRemoteSocketAddress());
                    ClientHandler clientHandler = new ClientHandler(clientSocket, dovizService);
                    clientHandlers.add(clientHandler);
                    clientExecutorService.submit(clientHandler);
                } catch (IOException e) {
                    if (running) { // Only log if server is supposed to be running
                        logger.error("Error accepting client connection: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.error("Could not start TCP server on port {}: {}", port, e.getMessage(), e);
            }
        } finally {
            stop();
        }
    }

    private void broadcastUpdates() {
        if (!running) return;
        try {
            List<String> ratesToUpdate = dovizService.getAvailableRates();
            synchronized (clientHandlers) {
                clientHandlers.removeIf(handler -> handler.getClientSocket().isClosed());
                for (ClientHandler handler : clientHandlers) {
                    for (String rateName : ratesToUpdate) {
                        if (handler.getSubscriptions().contains(rateName)) {
                            handler.sendRateUpdate(rateName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error during broadcastUpdates: {}", e.getMessage(), e);
        }
    }
    


    @PreDestroy
    public void stop() {
        running = false;
        logger.info("Stopping TCP Server...");
        try {
            synchronized (clientHandlers) {
                for (ClientHandler handler : clientHandlers) {
                    handler.stopHandler();
                }
                clientHandlers.clear();
            }
            clientExecutorService.shutdown();
            try {
                if (!clientExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    clientExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            rateUpdateScheduler.shutdown();
             try {
                if (!rateUpdateScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    rateUpdateScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                rateUpdateScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            logger.info("TCP Server stopped.");
        } catch (IOException e) {
            logger.error("Error stopping TCP server: {}", e.getMessage());
        }
    }
}
