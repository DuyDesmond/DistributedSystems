package com.filesync.client.service;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.filesync.client.config.ClientConfig;
import com.filesync.common.dto.SyncEventDto;

/**
 * WebSocket client for real-time file synchronization events
 */
public class WebSocketSyncClient extends WebSocketClient {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketSyncClient.class);
    
    private final ClientConfig config;
    private final SyncEventHandler eventHandler;
    private final ScheduledExecutorService executorService;
    private final ObjectMapper objectMapper;
    
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    
    private static final int RECONNECT_DELAY_SECONDS = 10;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    
    public interface SyncEventHandler {
        void handleFileChangeEvent(SyncEventDto event);
        void handleConflictEvent(SyncEventDto event);
        void handleConnectionStatusChange(boolean connected);
    }
    
    public WebSocketSyncClient(ClientConfig config, SyncEventHandler eventHandler, 
                              ScheduledExecutorService executorService) {
        super(buildWebSocketUri(config));
        this.config = config;
        this.eventHandler = eventHandler;
        this.executorService = executorService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Add authorization header if token is available
        if (config.getToken() != null && !config.getToken().isEmpty()) {
            addHeader("Authorization", "Bearer " + config.getToken());
        }
        
        // Schedule heartbeat
        scheduleHeartbeat();
    }
    
    /**
     * Build WebSocket URI from server URL
     */
    private static URI buildWebSocketUri(ClientConfig config) {
        try {
            String serverUrl = config.getServerUrl();
            String wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://");
            if (!wsUrl.endsWith("/")) {
                wsUrl += "/";
            }
            wsUrl += "ws/sync";
            return URI.create(wsUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build WebSocket URI", e);
        }
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        connected.set(true);
        logger.info("WebSocket connection established. Status: {}", handshake.getHttpStatus());
        
        // Send STOMP CONNECT frame first
        sendStompConnect();
        
        // Don't subscribe immediately - wait for CONNECTED response
        // subscribeToChannels() will be called when we receive CONNECTED frame
        
        // Notify handler about connection status
        eventHandler.handleConnectionStatusChange(true);
    }
    
    @Override
    public void onMessage(String message) {
        logger.debug("Received WebSocket message: {}", message);
        
        try {
            // Handle STOMP CONNECTED frame
            if (message.startsWith("CONNECTED")) {
                logger.info("Received STOMP CONNECTED frame - subscribing to channels");
                subscribeToChannels();
                return;
            }
            
            // Handle STOMP ERROR frame
            if (message.startsWith("ERROR")) {
                logger.error("Received STOMP ERROR frame: {}", message);
                return;
            }
            
            // Parse STOMP MESSAGE frame
            if (message.startsWith("MESSAGE")) {
                String[] lines = message.split("\n");
                String body = null;
                
                // Find message body (after empty line)
                boolean foundEmptyLine = false;
                for (String line : lines) {
                    if (foundEmptyLine && !line.trim().isEmpty()) {
                        body = line.trim();
                        break;
                    }
                    if (line.trim().isEmpty()) {
                        foundEmptyLine = true;
                    }
                }
                
                if (body != null) {
                    SyncEventDto event = objectMapper.readValue(body, SyncEventDto.class);
                    handleSyncEvent(event);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing WebSocket message", e);
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        connected.set(false);
        logger.warn("WebSocket connection closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
        
        // Notify handler about connection status
        try {
            eventHandler.handleConnectionStatusChange(false);
        } catch (Exception e) {
            logger.error("Error notifying connection status change", e);
        }
        
        // Schedule reconnection if needed and the service is still operational
        if (shouldReconnect.get() && !executorService.isShutdown()) {
            scheduleReconnection();
        }
    }
    
    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error occurred", ex);
        connected.set(false);
        eventHandler.handleConnectionStatusChange(false);
    }
    
    /**
     * Subscribe to file change and conflict channels
     */
    private void subscribeToChannels() {
        try {
            // Subscribe to file changes
            String fileChangeSub = createSubscriptionMessage("/user/queue/file-changes");
            send(fileChangeSub);
            
            // Subscribe to conflicts
            String conflictSub = createSubscriptionMessage("/user/queue/conflicts");
            send(conflictSub);
            
            logger.info("Subscribed to WebSocket channels");
        } catch (Exception e) {
            logger.error("Error subscribing to WebSocket channels", e);
        }
    }
    
    /**
     * Send STOMP CONNECT frame
     */
    private void sendStompConnect() {
        try {
            StringBuilder connectFrame = new StringBuilder();
            connectFrame.append("CONNECT\n");
            connectFrame.append("accept-version:1.0,1.1,1.2\n");
            connectFrame.append("host:").append(getURI().getHost()).append("\n");
            
            if (config.getToken() != null && !config.getToken().isEmpty()) {
                connectFrame.append("Authorization:Bearer ").append(config.getToken()).append("\n");
            }
            
            connectFrame.append("\n");  // Empty line separates headers from body
            connectFrame.append('\0');  // Null terminator for STOMP protocol
            
            send(connectFrame.toString());
            logger.debug("Sent STOMP CONNECT frame");
        } catch (Exception e) {
            logger.error("Error sending STOMP CONNECT frame", e);
        }
    }
    
    /**
     * Create STOMP subscription message
     */
    private String createSubscriptionMessage(String destination) {
        return "SUBSCRIBE\n" +
               "id:sub-" + System.currentTimeMillis() + "\n" +
               "destination:" + destination + "\n" +
               "\n";
    }
    
    /**
     * Handle incoming sync events
     */
    private void handleSyncEvent(SyncEventDto event) {
        try {
            String eventType = event.getEventType();
            
            if ("CONFLICT".equals(eventType)) {
                eventHandler.handleConflictEvent(event);
            } else {
                eventHandler.handleFileChangeEvent(event);
            }
        } catch (Exception e) {
            logger.error("Error handling sync event", e);
        }
    }
    
    /**
     * Send heartbeat to keep connection alive
     */
    private void sendHeartbeat() {
        // Check if connection is valid and executor is not shutdown
        if (connected.get() && shouldReconnect.get() && !executorService.isShutdown()) {
            try {
                SyncEventDto heartbeat = new SyncEventDto();
                heartbeat.setEventType("HEARTBEAT");
                heartbeat.setClientId("client-" + System.currentTimeMillis());
                
                String heartbeatMessage = "SEND\n" +
                                        "destination:/app/heartbeat\n" +
                                        "content-type:application/json\n" +
                                        "\n" +
                                        objectMapper.writeValueAsString(heartbeat) + "\n" + '\0';
                
                send(heartbeatMessage);
                logger.debug("Sent heartbeat");
            } catch (Exception e) {
                logger.error("Error sending heartbeat", e);
                // If we can't send heartbeat, the connection might be broken
                connected.set(false);
            }
        }
    }
    
    /**
     * Schedule periodic heartbeat
     */
    private void scheduleHeartbeat() {
        try {
            // Check if executor service is shutdown before scheduling
            if (executorService.isShutdown() || executorService.isTerminated()) {
                logger.debug("Executor service is shutdown, skipping heartbeat scheduling");
                return;
            }
            
            executorService.scheduleWithFixedDelay(this::sendHeartbeat, 
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            logger.debug("Failed to schedule heartbeat - executor service is shutting down");
        } catch (Exception e) {
            logger.error("Unexpected error scheduling heartbeat", e);
        }
    }
    
    /**
     * Schedule reconnection attempt
     */
    private void scheduleReconnection() {
        // First check if we should even attempt reconnection
        if (!shouldReconnect.get()) {
            logger.debug("Reconnection disabled, skipping reconnection attempt");
            return;
        }
        
        try {
            // Check if executor service is shutdown before scheduling
            if (executorService.isShutdown() || executorService.isTerminated()) {
                logger.debug("Executor service is shutdown, skipping reconnection attempt");
                return;
            }
            
            executorService.schedule(() -> {
                // Double-check shouldReconnect inside the scheduled task
                if (shouldReconnect.get() && !connected.get()) {
                    logger.info("Attempting to reconnect WebSocket...");
                    try {
                        reconnect();
                    } catch (Exception e) {
                        logger.error("Failed to reconnect WebSocket", e);
                        // Schedule another reconnection attempt with additional safety check
                        if (shouldReconnect.get() && !executorService.isShutdown()) {
                            scheduleReconnection();
                        }
                    }
                }
            }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            logger.debug("Failed to schedule reconnection - executor service is shutting down");
        } catch (Exception e) {
            logger.error("Unexpected error scheduling reconnection", e);
        }
    }
    
    /**
     * Update authentication token
     */
    public void updateAuthToken(String token) {
        logger.info("Updating auth token");
        
        // Update headers first
        clearHeaders();
        if (token != null && !token.isEmpty()) {
            addHeader("Authorization", "Bearer " + token);
        }
        
        // If we're currently connected, we need to reconnect to apply the new token
        // However, if this is being called from the WebSocket thread (like in onOpen), 
        // we need to do the reconnection asynchronously to avoid IllegalStateException
        if (connected.get()) {
            // Check if we're in the WebSocket thread by checking the thread name
            String threadName = Thread.currentThread().getName();
            if (threadName.contains("WebSocket") || threadName.contains("Java-WebSocket")) {
                
                logger.debug("Detected WebSocket thread - scheduling async reconnect");
                // Schedule reconnection on a separate thread
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100); // Small delay to ensure onOpen completes
                        close();
                        reconnect();
                    } catch (Exception e) {
                        logger.error("Failed to reconnect with new token (async)", e);
                    }
                });
            } else {
                // Safe to reconnect immediately
                try {
                    close();
                    reconnect();
                } catch (Exception e) {
                    logger.error("Failed to reconnect with new token", e);
                }
            }
        }
    }
    
    /**
     * Check if WebSocket is connected
     */
    public boolean isConnected() {
        return connected.get();
    }
    
    /**
     * Gracefully shutdown the WebSocket client
     */
    public void shutdown() {
        logger.info("Shutting down WebSocket client...");
        
        // Stop reconnection attempts first
        shouldReconnect.set(false);
        
        // Set connection status to false
        connected.set(false);
        
        // Close the WebSocket connection
        if (getConnection() != null && !getConnection().isClosed()) {
            try {
                close();
            } catch (Exception e) {
                logger.warn("Error closing WebSocket connection", e);
            }
        }
        
        logger.info("WebSocket client shutdown complete");
    }
}