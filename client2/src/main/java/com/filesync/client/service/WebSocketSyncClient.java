package com.filesync.client.service;

import java.net.URI;
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
        
        // Subscribe to file changes and conflicts
        subscribeToChannels();
        
        // Notify handler about connection status
        eventHandler.handleConnectionStatusChange(true);
    }
    
    @Override
    public void onMessage(String message) {
        logger.debug("Received WebSocket message: {}", message);
        
        try {
            // Parse STOMP frame
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
        eventHandler.handleConnectionStatusChange(false);
        
        // Schedule reconnection if needed
        if (shouldReconnect.get()) {
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
     * Create STOMP subscription message
     */
    private String createSubscriptionMessage(String destination) {
        return "SUBSCRIBE\n" +
               "id:sub-" + System.currentTimeMillis() + "\n" +
               "destination:" + destination + "\n" +
               "\n\n\0";
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
        if (connected.get()) {
            try {
                SyncEventDto heartbeat = new SyncEventDto();
                heartbeat.setEventType("HEARTBEAT");
                heartbeat.setClientId("client-" + System.currentTimeMillis());
                
                String heartbeatMessage = "SEND\n" +
                                        "destination:/app/heartbeat\n" +
                                        "content-type:application/json\n" +
                                        "\n" +
                                        objectMapper.writeValueAsString(heartbeat) + "\n\0";
                
                send(heartbeatMessage);
                logger.debug("Sent heartbeat");
            } catch (Exception e) {
                logger.error("Error sending heartbeat", e);
            }
        }
    }
    
    /**
     * Schedule periodic heartbeat
     */
    private void scheduleHeartbeat() {
        executorService.scheduleWithFixedDelay(this::sendHeartbeat, 
            HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * Schedule reconnection attempt
     */
    private void scheduleReconnection() {
        executorService.schedule(() -> {
            if (shouldReconnect.get() && !connected.get()) {
                logger.info("Attempting to reconnect WebSocket...");
                try {
                    reconnect();
                } catch (Exception e) {
                    logger.error("Failed to reconnect WebSocket", e);
                    // Schedule another reconnection attempt
                    scheduleReconnection();
                }
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * Update authentication token
     */
    public void updateAuthToken(String token) {
        // Close current connection and reconnect with new token
        if (connected.get()) {
            close();
        }
        
        // Update headers and reconnect
        clearHeaders();
        if (token != null && !token.isEmpty()) {
            addHeader("Authorization", "Bearer " + token);
        }
        
        try {
            reconnect();
        } catch (Exception e) {
            logger.error("Failed to reconnect with new token", e);
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
        shouldReconnect.set(false);
        if (connected.get()) {
            close();
        }
        logger.info("WebSocket client shutdown");
    }
}