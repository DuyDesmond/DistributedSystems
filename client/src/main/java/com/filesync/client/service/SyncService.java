package com.filesync.client.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.filesync.client.config.ClientConfig;
import com.filesync.common.dto.AuthDto;
import com.filesync.common.dto.FileDto;
import com.filesync.common.dto.SyncEventDto;

/**
 * Synchronization Service for handling file sync with server
 * Now supports both polling and event-driven real-time sync
 */
public class SyncService implements WebSocketSyncClient.SyncEventHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    
    private final ClientConfig config;
    private final ScheduledExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    
    private final BlockingQueue<SyncTask> syncQueue = new LinkedBlockingQueue<>();
    private boolean running = false;
    
    // WebSocket support for real-time sync
    private WebSocketSyncClient webSocketClient;
    private final AtomicBoolean webSocketConnected = new AtomicBoolean(false);
    
    // Adaptive polling intervals based on WebSocket connection status
    private static final int POLLING_INTERVAL_CONNECTED = 300; // 5 minutes when WebSocket connected
    private static final int POLLING_INTERVAL_DISCONNECTED = 30; // 30 seconds when disconnected
    
    public SyncService(ClientConfig config, ScheduledExecutorService executorService) {
        this.config = config;
        this.executorService = executorService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.createDefault();
        
        startSyncProcessor();
    }
    
    // WebSocket Event Handler implementation
    @Override
    public void handleFileChangeEvent(SyncEventDto event) {
        logger.info("Received real-time file change: {} for {}", event.getEventType(), event.getFilePath());
        
        try {
            // Don't process events from our own client
            if (config.getClientId().equals(event.getClientId())) {
                logger.debug("Ignoring event from our own client: {}", event.getClientId());
                return;
            }
            
            String eventType = event.getEventType();
            
            switch (eventType) {
                case "CREATE":
                case "MODIFY":
                    // Download the file that was created/modified
                    downloadFileFromEvent(event);
                    break;
                case "DELETE":
                    // Delete the local file
                    deleteLocalFileFromEvent(event);
                    break;
                default:
                    logger.warn("Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            logger.error("Error handling file change event: {}", event, e);
        }
    }
    
    @Override
    public void handleConflictEvent(SyncEventDto event) {
        logger.warn("Conflict detected for file: {} - {}", event.getFilePath(), event.getEventType());
        
        try {
            // For now, just log the conflict
            // In a more advanced implementation, you could:
            // 1. Show user notification
            // 2. Create conflict resolution UI
            // 3. Automatically resolve using Last-Write-Wins strategy
            
            logger.info("Conflict resolution needed for: {}", event.getFilePath());
            // Could trigger UI notification here
            
        } catch (Exception e) {
            logger.error("Error handling conflict event: {}", event, e);
        }
    }
    
    @Override
    public void handleConnectionStatusChange(boolean connected) {
        webSocketConnected.set(connected);
        
        if (connected) {
            logger.info("WebSocket connected - switching to real-time sync mode");
            // Update WebSocket client with current auth token
            if (webSocketClient != null) {
                webSocketClient.updateAuthToken(config.getToken());
            }
        } else {
            logger.warn("WebSocket disconnected - falling back to polling mode");
        }
        
        // Adjust polling frequency based on WebSocket status
        adjustPollingFrequency();
    }
    
    // Helper methods for WebSocket event handling
    private void downloadFileFromEvent(SyncEventDto event) {
        try {
            Path localPath = Paths.get(config.getLocalSyncPath(), event.getFilePath());
            
            // Find the file ID by path (simplified - in production use proper mapping)
            String fileId = findFileIdByPath(event.getFilePath());
            if (fileId != null) {
                downloadFile(fileId, localPath);
                logger.info("Downloaded file from real-time event: {}", event.getFilePath());
            } else {
                logger.warn("Could not find file ID for path: {}", event.getFilePath());
            }
        } catch (Exception e) {
            logger.error("Error downloading file from event: {}", event.getFilePath(), e);
        }
    }
    
    private void deleteLocalFileFromEvent(SyncEventDto event) {
        try {
            Path localPath = Paths.get(config.getLocalSyncPath(), event.getFilePath());
            if (Files.exists(localPath)) {
                Files.delete(localPath);
                logger.info("Deleted file from real-time event: {}", event.getFilePath());
            }
        } catch (Exception e) {
            logger.error("Error deleting file from event: {}", event.getFilePath(), e);
        }
    }
    
    private String findFileIdByPath(String filePath) {
        // This is a simplified implementation
        // In production, you'd want to maintain a local mapping or query the server
        try {
            HttpGet get = new HttpGet(config.getServerUrl() + "/files/");
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getCode() == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    FileDto[] files = objectMapper.readValue(responseBody, FileDto[].class);
                    
                    for (FileDto file : files) {
                        if (file.getFilePath().equals(filePath)) {
                            return file.getFileId();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error finding file ID for path: {}", filePath, e);
        }
        return null;
    }
    
    private void adjustPollingFrequency() {
        // This would require restructuring the periodic sync scheduling
        // For now, just log the change
        int newInterval = webSocketConnected.get() ? POLLING_INTERVAL_CONNECTED : POLLING_INTERVAL_DISCONNECTED;
        logger.info("Adjusting polling frequency to {} seconds", newInterval);
    }
    
    private void initializeWebSocketClient() {
        if (config.getToken() != null && !config.getToken().isEmpty()) {
            try {
                webSocketClient = new WebSocketSyncClient(config, this, executorService);
                webSocketClient.connect();
                logger.info("WebSocket client initialized and connecting...");
            } catch (Exception e) {
                logger.error("Failed to initialize WebSocket client", e);
            }
        }
    }
    
    public boolean login(String username, String password) {
        try {
            AuthDto loginRequest = AuthDto.loginRequest(username, password);
            String json = objectMapper.writeValueAsString(loginRequest);
            
            HttpPost post = new HttpPost(config.getServerUrl() + "/auth/login");
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            post.setHeader("Content-Type", "application/json");
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (response.getCode() == 200) {
                    AuthDto authResponse = objectMapper.readValue(responseBody, AuthDto.class);
                    config.setUsername(username);
                    config.setToken(authResponse.getAccessToken());
                    config.setRefreshToken(authResponse.getRefreshToken());
                    
                    // Update client ID to be user-specific
                    config.updateClientIdForUser(username);
                    
                    config.saveConfig();
                    
                    logger.info("Login successful for user: {}", username);
                    
                    // Initialize WebSocket client for real-time sync
                    initializeWebSocketClient();
                    
                    return true;
                } else {
                    logger.error("Login failed: {}", responseBody);
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Login error", e);
            return false;
        }
    }
    
    public boolean register(String username, String email, String password) {
        try {
            AuthDto registerRequest = AuthDto.registerRequest(username, email, password);
            String json = objectMapper.writeValueAsString(registerRequest);
            
            HttpPost post = new HttpPost(config.getServerUrl() + "/auth/register");
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            post.setHeader("Content-Type", "application/json");
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (response.getCode() == 200) {
                    logger.info("Registration successful for user: {}", username);
                    return login(username, password); // Auto-login after registration
                } else {
                    logger.error("Registration failed: {}", responseBody);
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Registration error", e);
            return false;
        }
    }
    
    public void queueFileForUpload(Path filePath) {
        syncQueue.offer(new SyncTask(SyncTask.Type.UPLOAD, filePath));
    }
    
    public void queueFileForDeletion(Path filePath) {
        syncQueue.offer(new SyncTask(SyncTask.Type.DELETE, filePath));
    }
    
    private void startSyncProcessor() {
        running = true;
        executorService.submit(this::processSyncQueue);
        
        // Schedule periodic sync
        executorService.scheduleAtFixedRate(
            this::performPeriodicSync,
            config.getSyncInterval(),
            config.getSyncInterval(),
            TimeUnit.SECONDS
        );
    }
    
    private void processSyncQueue() {
        while (running) {
            try {
                SyncTask task = syncQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void processTask(SyncTask task) {
        try {
            switch (task.getType()) {
                case UPLOAD:
                    uploadFile(task.getFilePath());
                    break;
                case DELETE:
                    deleteFile(task.getFilePath());
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing sync task: " + task, e);
        }
    }
    
    private void uploadFile(Path filePath) {
        try {
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return;
            }
            
            String relativePath = getRelativePath(filePath);
            
            HttpPost post = new HttpPost(config.getServerUrl() + "/files/upload");
            post.setHeader("Authorization", "Bearer " + config.getToken());
            
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", filePath.toFile(), ContentType.APPLICATION_OCTET_STREAM, filePath.getFileName().toString());
            builder.addTextBody("path", relativePath, ContentType.TEXT_PLAIN);
            
            post.setEntity(builder.build());
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                if (response.getCode() == 200) {
                    logger.info("File uploaded successfully: {}", relativePath);
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    logger.error("File upload failed: {} - {}", relativePath, responseBody);
                }
            }
        } catch (Exception e) {
            logger.error("Error uploading file: " + filePath, e);
        }
    }
    
    private void deleteFile(Path filePath) {
        try {
            String relativePath = getRelativePath(filePath);
            
            // This is simplified - in reality, you'd need to get the file ID first
            logger.info("File deleted locally: {}", relativePath);
            
        } catch (Exception e) {
            logger.error("Error processing file deletion: " + filePath, e);
        }
    }
    
    private void performPeriodicSync() {
        if (config.getToken() == null) {
            return; // Not logged in
        }
        
        try {
            // Download changes from server
            downloadServerChanges();
        } catch (Exception e) {
            logger.error("Error during periodic sync", e);
        }
    }
    
    private void downloadServerChanges() {
        try {
            HttpGet get = new HttpGet(config.getServerUrl() + "/files/");
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getCode() == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    FileDto[] files = objectMapper.readValue(responseBody, FileDto[].class);
                    
                    for (FileDto file : files) {
                        downloadFileIfNeeded(file);
                    }
                } else if (response.getCode() == 401) {
                    // Token expired, try to refresh
                    refreshToken();
                }
            }
        } catch (Exception e) {
            logger.error("Error downloading server changes", e);
        }
    }
    
    private void downloadFileIfNeeded(FileDto serverFile) {
        try {
            Path localPath = Paths.get(config.getLocalSyncPath(), serverFile.getFilePath());
            
            // Don't download if file was recently deleted
            // Check if the file was deleted in this session (simple approach)
            if (!Files.exists(localPath)) {
                // TODO: Add proper deletion tracking with database or file tracking
                // For now, just add a warning that file will be re-downloaded
                logger.warn("File {} doesn't exist locally and will be downloaded. " +
                           "To prevent re-download of deleted files, use EnhancedSyncService instead.", 
                           serverFile.getFilePath());
                downloadFile(serverFile.getFileId(), localPath);
            }
        } catch (Exception e) {
            logger.error("Error checking if file download needed: " + serverFile.getFilePath(), e);
        }
    }
    
    private void downloadFile(String fileId, Path localPath) {
        try {
            HttpGet get = new HttpGet(config.getServerUrl() + "/files/" + fileId + "/download");
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getCode() == 200) {
                    Files.createDirectories(localPath.getParent());
                    Files.copy(response.getEntity().getContent(), localPath);
                    logger.info("File downloaded: {}", localPath);
                }
            }
        } catch (Exception e) {
            logger.error("Error downloading file: " + fileId, e);
        }
    }
    
    private void refreshToken() {
        try {
            AuthDto refreshRequest = new AuthDto();
            refreshRequest.setRefreshToken(config.getRefreshToken());
            String json = objectMapper.writeValueAsString(refreshRequest);
            
            HttpPost post = new HttpPost(config.getServerUrl() + "/auth/refresh");
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            post.setHeader("Content-Type", "application/json");
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                if (response.getCode() == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    AuthDto authResponse = objectMapper.readValue(responseBody, AuthDto.class);
                    config.setToken(authResponse.getAccessToken());
                    config.setRefreshToken(authResponse.getRefreshToken());
                    config.saveConfig();
                    logger.info("Token refreshed successfully");
                }
            }
        } catch (Exception e) {
            logger.error("Error refreshing token", e);
        }
    }
    
    private String getRelativePath(Path filePath) {
        Path syncPath = Paths.get(config.getLocalSyncPath()).toAbsolutePath();
        Path absoluteFilePath = filePath.toAbsolutePath();
        return syncPath.relativize(absoluteFilePath).toString().replace("\\", "/");
    }
    
    public boolean isLoggedIn() {
        return config.getToken() != null && config.getUsername() != null;
    }
    
    public void stop() {
        running = false;
        
        // Shutdown WebSocket client
        if (webSocketClient != null) {
            webSocketClient.shutdown();
        }
        
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.error("Error closing HTTP client", e);
        }
    }
    
    // Inner class for sync tasks
    private static class SyncTask {
        enum Type { UPLOAD, DELETE }
        
        private final Type type;
        private final Path filePath;
        
        public SyncTask(Type type, Path filePath) {
            this.type = type;
            this.filePath = filePath;
        }
        
        public Type getType() { return type; }
        public Path getFilePath() { return filePath; }
        
        @Override
        public String toString() {
            return "SyncTask{type=" + type + ", filePath=" + filePath + "}";
        }
    }
}
