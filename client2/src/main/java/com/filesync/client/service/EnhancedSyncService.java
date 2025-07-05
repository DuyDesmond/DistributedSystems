package com.filesync.client.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.filesync.client.config.ClientConfig;
import com.filesync.client.ui.ConflictResolutionController;
import com.filesync.common.dto.AuthDto;
import com.filesync.common.dto.FileChunkDto;
import com.filesync.common.dto.FileDto;
import com.filesync.common.dto.SyncEventDto;
import com.filesync.common.model.VersionVector;

import javafx.application.Platform;

/**
 * Enhanced Synchronization Service with version vector support and real-time WebSocket sync and advanced chunking
 */
public class EnhancedSyncService implements WebSocketSyncClient.SyncEventHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedSyncService.class);
    
    // Chunking configuration
    private static final long CHUNK_SIZE_THRESHOLD = 5 * 1024 * 1024; // 50MB - files larger than this will be chunked
    private static final long CHUNK_SIZE =  1024 * 1024; // 1MB per chunk
    private static final long MIN_CHUNK_SIZE = 1 * 1024 * 1024; // 1MB minimum
    private static final int MAX_CONCURRENT_CHUNKS = 3; // Maximum parallel chunk uploads
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second base delay
    
    private final ClientConfig config;
    private final DatabaseService databaseService;
    private final ScheduledExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final String clientId;
    
    private final BlockingQueue<SyncTask> syncQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    
    // WebSocket support for real-time sync
    private WebSocketSyncClient webSocketClient;
    private final AtomicBoolean webSocketConnected = new AtomicBoolean(false);
    
    // Adaptive polling intervals based on WebSocket connection status
    private static final int POLLING_INTERVAL_CONNECTED = 300; // 5 minutes when WebSocket connected
    private static final int POLLING_INTERVAL_DISCONNECTED = 30; // 30 seconds when disconnected
    
    // Chunking control
    private final Semaphore chunkUploadSemaphore = new Semaphore(MAX_CONCURRENT_CHUNKS);
    
    // Conflict management
    private ConflictManager conflictManager;
    
    /**
     * Simple DTO for chunk upload session response
     */
    public static class ChunkUploadSession {
        private String sessionId;
        
        public ChunkUploadSession() {}
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
    
    /**
     * Calculate SHA-256 checksum of file data
     */
    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Get relative path for sync
     */
    private String getRelativePath(Path filePath) {
        Path syncRoot = Paths.get(config.getLocalSyncPath());
        Path relativePath = syncRoot.relativize(filePath);
        return relativePath.toString().replace('\\', '/'); // Normalize to forward slashes
    }

    public EnhancedSyncService(ClientConfig config, DatabaseService databaseService, 
                              ScheduledExecutorService executorService) {
        this.config = config;
        this.databaseService = databaseService;
        this.executorService = executorService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.createDefault();
        this.clientId = config.getClientId(); // Use deterministic client ID from config
        
        // Initialize conflict manager
        this.conflictManager = new ConflictManager(this, config);
        
        startSyncProcessor();
        schedulePeriodicSync();
        
        // If user is already authenticated, initialize sync and perform initial scan
        if (isAuthenticated()) {
            logger.info("User already authenticated at startup - initializing sync");
            initializeWebSocketSync();
            executorService.schedule(this::performInitialDirectoryScan, 5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Start background sync processor
     */
    private void startSyncProcessor() {
        running = true;
        executorService.submit(() -> {
            while (running) {
                try {
                    SyncTask task = syncQueue.poll(5, TimeUnit.SECONDS);
                    if (task != null) {
                        processSyncTask(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error processing sync task", e);
                }
            }
        });
    }
    
    /**
     * Schedule periodic sync checks
     */
    private void schedulePeriodicSync() {
        executorService.scheduleWithFixedDelay(this::performPeriodicSync, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Perform periodic synchronization
     */
    private void performPeriodicSync() {
        if (!isAuthenticated()) {
            return;
        }
        
        try {
            // Process pending files from database
            var pendingFiles = databaseService.getPendingSyncFiles();
            for (var entry : pendingFiles.entrySet()) {
                queueFileSync(entry.getKey(), SyncOperation.UPLOAD);
            }
            
            // Get updates from server
            getServerUpdates();
            
            // Clean up old DELETED markers periodically (every hour)
            // This prevents database bloat while giving enough time for sync coordination
            cleanupOldDeletedMarkers();
            
        } catch (Exception e) {
            logger.error("Error during periodic sync", e);
        }
    }
    
    /**
     * Queue file for synchronization
     */
    public void queueFileSync(String filePath, SyncOperation operation) {
        syncQueue.offer(new SyncTask(filePath, operation));
        databaseService.addToSyncQueue(filePath, operation.name(), getPriority(operation));
        logger.info("Queued file for sync: {} ({})", filePath, operation);
    }
    
    /**
     * Process individual sync task
     */
    private void processSyncTask(SyncTask task) {
        try {
            switch (task.operation) {
                case UPLOAD -> uploadFile(task.filePath);
                case DOWNLOAD -> downloadFile(task.filePath);
                case DELETE -> deleteFile(task.filePath);
                case CONFLICT_RESOLVE -> resolveConflict(task.filePath);
            }
        } catch (Exception e) {
            logger.error("Failed to process sync task: {} ({})", task.filePath, task.operation, e);
        }
    }
    
    /**
     * Check if user is authenticated
     */
    private boolean isAuthenticated() {
        boolean authenticated = config.getToken() != null && !config.getToken().isEmpty();
        if (!authenticated) {
            logger.debug("User not authenticated - token: {}", config.getToken() == null ? "null" : "empty");
        }
        return authenticated;
    }
    
    /**
     * Initialize WebSocket client for real-time sync when user is authenticated
     */
    public void initializeWebSocketSync() {
        if (isAuthenticated() && webSocketClient == null) {
            initializeWebSocketClient();
        }
    }
    
    /**
     * Get priority for sync operation
     */
    private int getPriority(SyncOperation operation) {
        return switch (operation) {
            case DELETE -> 1;
            case CONFLICT_RESOLVE -> 2;
            case UPLOAD -> 3;
            case DOWNLOAD -> 4;
        };
    }
      /**
     * Upload file implementation with chunking support
     */
    private void uploadFile(String filePath) {
        logger.info("Starting upload for file: {}", filePath);
        
        try {
            Path file = Paths.get(config.getLocalSyncPath(), filePath);
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                logger.warn("Skipping upload - file not found or not regular: {}", file);
                return;
            }
            
            // Check if file was recently deleted to prevent race conditions
            if (isRecentlyDeleted(filePath)) {
                // Check if file actually exists now - if so, clear deletion and proceed
                if (Files.exists(file)) {
                    logger.info("File was previously deleted but now exists again: {}", filePath);
                    databaseService.clearDeletionStatus(filePath);
                } else {
                    logger.info("Skipping upload of file marked as deleted: {}", filePath);
                    return;
                }
            }
            
            long fileSize = Files.size(file);
            
            // Use chunked upload for large files
            if (shouldChunkFile(fileSize)) {
                uploadFileWithChunking(file, filePath);
            } else {
                uploadFileDirectly(file, filePath);
            }
            
        } catch (IOException e) {
            logger.error("Error uploading file: " + filePath, e);
        }
    }
    
    /**
     * Check if file should be chunked based on size
     */
    private boolean shouldChunkFile(long fileSize) {
        return fileSize >= CHUNK_SIZE_THRESHOLD; // Use configured threshold
    }
    
    /**
     * Upload file using chunking for large files
     */
    private void uploadFileWithChunking(Path file, String relativePath) throws IOException {
        logger.info("Starting chunked upload for large file: {} ({} bytes)", relativePath, Files.size(file));
        
        String fileId = UUID.randomUUID().toString();
        byte[] fileBytes = Files.readAllBytes(file);
        
        // Create chunks
        List<FileChunkDto> chunks = createChunks(fileBytes, fileId);
        logger.info("Created {} chunks for file: {}", chunks.size(), relativePath);
        
        // Initiate chunked upload session
        String sessionId = initiateChunkedUploadSession(fileId, relativePath, chunks.size(), (long) fileBytes.length);
        
        // Upload chunks sequentially
        for (FileChunkDto chunk : chunks) {
            uploadChunk(sessionId, chunk);
            logger.debug("Uploaded chunk {}/{} for file: {}", 
                chunk.getChunkIndex() + 1, chunks.size(), relativePath);
        }
        
        logger.info("Completed chunked upload for file: {}", relativePath);
    }
    
    /**
     * Upload file directly for smaller files
     */
    private void uploadFileDirectly(Path file, String relativePath) throws IOException {
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            logger.warn("Skipping upload - file not found: {}", file);
            return;
        }
        
        // Calculate version vector and checksum
        VersionVector versionVector = databaseService.getFileVersionVector(relativePath);
        if (versionVector == null) {
            versionVector = new VersionVector();
        }
        versionVector.increment(clientId);
        
        byte[] fileData = Files.readAllBytes(file);
        String checksum = calculateChecksum(fileData);
        
        HttpPost post = new HttpPost(config.getServerUrl() + "/files/upload");
        post.setHeader("Authorization", "Bearer " + config.getToken());
        
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addBinaryBody("file", file.toFile(), ContentType.APPLICATION_OCTET_STREAM, file.getFileName().toString());
        builder.addTextBody("path", relativePath, ContentType.TEXT_PLAIN);
        builder.addTextBody("checksum", checksum, ContentType.TEXT_PLAIN);
        builder.addTextBody("versionVector", objectMapper.writeValueAsString(versionVector), ContentType.APPLICATION_JSON);
        builder.addTextBody("clientId", clientId, ContentType.TEXT_PLAIN);
        
        post.setEntity(builder.build());
        
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            if (response.getCode() == 200) {
                // Update local database
                databaseService.updateFileMetadata(relativePath, checksum, Files.size(file), versionVector);
                databaseService.markFileSynced(relativePath);
                logger.info("File uploaded successfully: {}", relativePath);
            } else {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (ParseException e) {
                    responseBody = "Failed to parse response: " + e.getMessage();
                }
                logger.error("File upload failed: {} - {}", relativePath, responseBody);
            }
        }
    }
    
    /**
     * Create chunks from file data
     */
    private List<FileChunkDto> createChunks(byte[] fileData, String fileId) {
        List<FileChunkDto> chunks = new ArrayList<>();
        long chunkSize = Math.min(CHUNK_SIZE, Math.max(MIN_CHUNK_SIZE, fileData.length / 10)); // Adaptive chunk size
        int totalChunks = (int) Math.ceil((double) fileData.length / chunkSize);
        String uploadSessionId = UUID.randomUUID().toString();
        
        for (int i = 0; i < totalChunks; i++) {
            int start = (int) (i * chunkSize);
            int end = Math.min(start + (int) chunkSize, fileData.length);
            byte[] chunkData = Arrays.copyOfRange(fileData, start, end);
            
            FileChunkDto chunk = new FileChunkDto(fileId, uploadSessionId, i, totalChunks);
            chunk.setChunkId(UUID.randomUUID().toString());
            chunk.setChunkData(chunkData);
            chunk.setChunkChecksum(calculateChecksum(chunkData));
            chunk.setIsLastChunk(i == totalChunks - 1);
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Initiate chunked upload session on server
     */
    private String initiateChunkedUploadSession(String fileId, String filePath, int totalChunks, long totalFileSize) throws IOException {
        HttpPost post = new HttpPost(config.getServerUrl() + "/files/upload/initiate-chunked");
        post.setHeader("Authorization", "Bearer " + config.getToken());
        
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("fileId", fileId, ContentType.TEXT_PLAIN);
        builder.addTextBody("filePath", filePath, ContentType.TEXT_PLAIN);
        builder.addTextBody("totalChunks", String.valueOf(totalChunks), ContentType.TEXT_PLAIN);
        builder.addTextBody("totalFileSize", String.valueOf(totalFileSize), ContentType.TEXT_PLAIN);
        
        post.setEntity(builder.build());
        
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            if (response.getCode() == 200) {
                String responseBody = EntityUtils.toString(response.getEntity());
                ChunkUploadSession session = objectMapper.readValue(responseBody, ChunkUploadSession.class);
                return session.getSessionId();
            } else {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new IOException("Failed to initiate chunked upload: " + responseBody);
            }
        } catch (ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }
    
    /**
     * Upload a single chunk to server
     */
    private void uploadChunk(String sessionId, FileChunkDto chunk) throws IOException {
        // Acquire permit for concurrent chunk upload
        try {
            chunkUploadSemaphore.acquire();
            
            HttpPost post = new HttpPost(config.getServerUrl() + "/files/upload/chunk");
            post.setHeader("Authorization", "Bearer " + config.getToken());
            
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("sessionId", sessionId, ContentType.TEXT_PLAIN);
            builder.addTextBody("chunkIndex", String.valueOf(chunk.getChunkIndex()), ContentType.TEXT_PLAIN);
            builder.addBinaryBody("chunkData", chunk.getChunkData(), ContentType.APPLICATION_OCTET_STREAM, "chunk");
            
            post.setEntity(builder.build());
            
            int attempt = 0;
            boolean success = false;
            while (attempt < MAX_RETRY_ATTEMPTS && !success) {
                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    if (response.getCode() == 200) {
                        success = true;
                    } else {
                        String responseBody;
                        try {
                            responseBody = EntityUtils.toString(response.getEntity());
                        } catch (ParseException e) {
                            responseBody = "Failed to parse response: " + e.getMessage();
                        }
                        throw new IOException("Chunk upload failed: " + responseBody);
                    }
                } catch (IOException e) {
                    attempt++;
                    logger.warn("Chunk upload failed, retrying {}/{}: {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        // Use exponential backoff without Thread.sleep
                        long delayMs = RETRY_DELAY_MS * attempt;
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Upload interrupted during retry", ie);
                        }
                    }
                }
            }
            
            if (!success) {
                throw new IOException("Failed to upload chunk after " + MAX_RETRY_ATTEMPTS + " attempts");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Chunk upload interrupted", e);
        } finally {
            chunkUploadSemaphore.release();
        }
    }
    
    /**
     * Download file implementation
     */
    private void downloadFile(String filePath) {
        logger.info("Downloading file: {}", filePath);
        
        // Check authentication first
        if (!isAuthenticated()) {
            logger.warn("Cannot download file - user not authenticated: {}", filePath);
            return;
        }
        
        try {
            String fileId = findFileIdByPath(filePath);
            if (fileId == null) {
                logger.error("File not found on server: {}. This may indicate:", filePath);
                logger.error("  1. File was never uploaded to server");
                logger.error("  2. File path mismatch between client and server");
                logger.error("  3. File belongs to different user");
                logger.error("  4. Database inconsistency on server");
                logAvailableFiles();
                
                // Attempt to upload the file if it exists locally
                Path localFilePath = Paths.get(config.getLocalSyncPath(), filePath);
                if (Files.exists(localFilePath)) {
                    logger.info("File exists locally, attempting to upload: {}", filePath);
                    queueFileSync(filePath, SyncOperation.UPLOAD);
                }
                return;
            }
            
            logger.debug("Found file ID {} for path: {}", fileId, filePath);
            
            String downloadUrl = config.getServerUrl() + "/files/" + fileId + "/download";
            HttpGet get = new HttpGet(downloadUrl);
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            logger.debug("Sending download request to: {}", downloadUrl);
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                logger.debug("Download response code: {} for file: {}", response.getCode(), filePath);
                
                if (response.getCode() == 200) {
                    Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
                    Files.createDirectories(localPath.getParent());
                    Files.copy(response.getEntity().getContent(), localPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    // Update local database
                    byte[] fileData = Files.readAllBytes(localPath);
                    String checksum = calculateChecksum(fileData);
                    long fileSize = Files.size(localPath);
                    
                    VersionVector versionVector = new VersionVector();
                    databaseService.storeFileVersionVector(fileId, filePath, versionVector, 
                        LocalDateTime.now(), fileSize, checksum);
                    databaseService.updateSyncStatus(filePath, "SYNCED");
                    
                    logger.info("File downloaded successfully: {}", filePath);
                } else {
                    String responseBody;
                    try {
                        responseBody = EntityUtils.toString(response.getEntity());
                    } catch (ParseException e) {
                        responseBody = "Failed to parse response: " + e.getMessage();
                    }
                    logger.error("File download failed with status {}: {} - Response: {}", 
                        response.getCode(), filePath, responseBody);
                }
            }
            
        } catch (IOException e) {
            logger.error("Error downloading file: {} - Exception: {}", filePath, e.getMessage(), e);
        }
    }

    /**
     * Delete file implementation
     */
    /**
     * Delete file implementation
     */
    private void deleteFile(String filePath) {
        deleteFileOnServer(filePath);
    }
    
    /**
     * Delete file on server only (used for immediate deletion)
     */
    private void deleteFileOnServer(String filePath) {
        logger.info("Deleting file on server: {}", filePath);
        
        try {
            String fileId = findFileIdByPath(filePath);
            if (fileId == null) {
                logger.warn("Could not find file ID for path: {}", filePath);
                return;
            }
            
            // Delete on server
            HttpDelete delete = new HttpDelete(config.getServerUrl() + "/files/" + fileId);
            delete.setHeader("Authorization", "Bearer " + config.getToken());
            
            try (CloseableHttpResponse response = httpClient.execute(delete)) {
                if (response.getCode() == 200) {
                    // Delete local file if exists
                    Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
                    Files.deleteIfExists(localPath);
                    
                    logger.info("File deleted successfully on server: {}", filePath);
                } else {
                    String responseBody;
                    try {
                        responseBody = EntityUtils.toString(response.getEntity());
                    } catch (ParseException e) {
                        responseBody = "Failed to parse response: " + e.getMessage();
                    }
                    logger.error("File deletion failed: {} - {}", filePath, responseBody);
                }
            }
            
        } catch (IOException e) {
            logger.error("Error deleting file: {}", filePath, e);
        }
    }

    /**
     * Resolve conflict implementation
     */
    private void resolveConflict(String filePath) {
        logger.info("Resolving conflict for: {}", filePath);
        
        try {
            // Use ConflictManager for coordinated UI-based conflict resolution
            conflictManager.handleConflict(filePath);
            
        } catch (Exception e) {
            logger.error("Error resolving conflict for: {}", filePath, e);
            // Fallback to simple Last-Write-Wins strategy
            fallbackConflictResolution(filePath);
        }
    }
    
    /**
     * Fallback conflict resolution using Last-Write-Wins strategy
     */
    private void fallbackConflictResolution(String filePath) {
        try {
            // Get local file info
            Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
            if (!Files.exists(localPath)) {
                // Local file deleted, download from server
                downloadFile(filePath);
                return;
            }
            
            // Check modification times and checksums
            VersionVector localVector = databaseService.getFileVersionVectorByPath(filePath);
            if (localVector != null) {
                // Try to upload our version (server will handle conflict detection)
                uploadFile(localPath.toString());
            }
            
        } catch (Exception e) {
            logger.error("Error in fallback conflict resolution for: {}", filePath, e);
        }
    }

    /**
     * Get server updates implementation
     */
    private void getServerUpdates() {
        logger.debug("Getting server updates");
        
        try {
            HttpGet get = new HttpGet(config.getServerUrl() + "/files/");
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getCode() == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    FileDto[] serverFiles = objectMapper.readValue(responseBody, FileDto[].class);
                    
                    // Create set of server file paths for quick lookup
                    Set<String> serverFilePaths = new HashSet<>();
                    for (FileDto serverFile : serverFiles) {
                        serverFilePaths.add(serverFile.getFilePath());
                        processServerFile(serverFile);
                    }
                    
                    // Clean up files that no longer exist on server
                    cleanupDeletedFiles(serverFilePaths);
                    
                } else if (response.getCode() == 401) {
                    // Token expired, needs re-authentication
                    logger.warn("Authentication token expired");
                }
            }
            
        } catch (Exception e) {
            logger.error("Error getting server updates", e);
        }
    }

    /**
     * Clean up files that no longer exist on server or are marked as deleted
     */
    private void cleanupDeletedFiles(Set<String> serverFilePaths) {
        try {
            // Get all files from local database
            Map<String, String> localFiles = databaseService.getAllTrackedFiles();
            
            for (Map.Entry<String, String> entry : localFiles.entrySet()) {
                String filePath = entry.getKey();
                String syncStatus = entry.getValue();
                
                // If file is marked as DELETED or no longer exists on server, clean it up
                if ("DELETED".equals(syncStatus) || !serverFilePaths.contains(filePath)) {
                    Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
                    
                    // Delete local file if it still exists
                    if (Files.exists(localPath)) {
                        try {
                            Files.delete(localPath);
                            logger.info("Cleaned up local file that was deleted on server: {}", filePath);
                        } catch (IOException e) {
                            logger.warn("Failed to delete local file: {}", filePath, e);
                        }
                    }
                    
                    // Remove from local database only if file doesn't exist on server
                    // Keep DELETED status if file was marked as deleted locally
                    if (!serverFilePaths.contains(filePath) && !"DELETED".equals(syncStatus)) {
                        databaseService.removeFileRecord(filePath);
                        logger.info("Removed database record for file deleted on server: {}", filePath);
                    } else if ("DELETED".equals(syncStatus)) {
                        // File was deleted locally, keep the DELETED marker for a while
                        logger.debug("Keeping DELETED marker for locally deleted file: {}", filePath);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error cleaning up deleted files", e);
        }
    }

    /**
     * Process individual server file for sync
     */
    private void processServerFile(FileDto serverFile) {
        try {
            String filePath = serverFile.getFilePath();
            Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
            
            // Check if file is marked as deleted locally FIRST
            String syncStatus = databaseService.getSyncStatus(filePath);
            if ("DELETED".equals(syncStatus)) {
                // File was deleted locally, don't sync
                logger.debug("Skipping sync for file marked as deleted: {}", filePath);
                return;
            }
            
            // Get local version vector
            VersionVector localVector = databaseService.getFileVersionVectorByPath(filePath);
            VersionVector serverVector = serverFile.getVersionVector();
            
            // Check if local file exists but is not being tracked (new file scenario)
            boolean localFileExists = Files.exists(localPath);
            
            if (localVector == null && !localFileExists) {
                // File doesn't exist locally and is not tracked - download it
                logger.debug("File not found locally, downloading: {}", filePath);
                queueFileSync(filePath, SyncOperation.DOWNLOAD);
            } else if (localVector == null && localFileExists) {
                // Local file exists but not tracked - this means it was just created locally
                // Upload it to sync with server
                logger.debug("Local file exists but not tracked, uploading: {}", filePath);
                queueFileSync(filePath, SyncOperation.UPLOAD);
            } else if (localVector != null && serverVector != null) {
                if (serverVector.dominates(localVector) && !localVector.dominates(serverVector)) {
                    // Server is newer - download
                    queueFileSync(filePath, SyncOperation.DOWNLOAD);
                } else if (localVector.dominates(serverVector) && !serverVector.dominates(localVector)) {
                    // Local is newer - upload
                    queueFileSync(filePath, SyncOperation.UPLOAD);
                } else if (localVector.isConcurrentWith(serverVector)) {
                    // Conflict - needs resolution
                    queueFileSync(filePath, SyncOperation.CONFLICT_RESOLVE);
                }
                // If vectors are equal, files are in sync - no action needed
            }
            
        } catch (Exception e) {
            logger.error("Error processing server file: {}", serverFile.getFilePath(), e);
        }
    }

    /**
     * Find file ID by path from server
     */
    private String findFileIdByPath(String relativePath) {
        try {
            String listFilesUrl = config.getServerUrl() + "/files/";
            HttpGet get = new HttpGet(listFilesUrl);
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            logger.debug("Requesting file list from server: {}", listFilesUrl);
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                logger.debug("File list response code: {}", response.getCode());
                
                if (response.getCode() == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    logger.debug("File list response body length: {}", responseBody.length());
                    
                    FileDto[] files = objectMapper.readValue(responseBody, FileDto[].class);
                    logger.debug("Found {} files from server", files.length);
                    
                    for (FileDto file : files) {
                        logger.debug("Server file: {} -> {}", file.getFilePath(), file.getFileId());
                        if (file.getFilePath().equals(relativePath)) {
                            logger.debug("Found matching file ID: {} for path: {}", file.getFileId(), relativePath);
                            return file.getFileId();
                        }
                    }
                    logger.warn("No matching file found on server for path: {}", relativePath);
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    logger.error("Failed to get file list from server. Status: {}, Response: {}", 
                        response.getCode(), responseBody);
                }
            }
        } catch (Exception e) {
            logger.error("Error finding file ID for path: {} - Exception: {}", relativePath, e.getMessage(), e);
        }
        return null;
    }

    /**
     * Public method to upload a specific file
     */
    public void uploadFileManually(String filePath) {
        if (!isAuthenticated()) {
            logger.warn("Cannot upload file - user not authenticated");
            return;
        }
        queueFileSync(filePath, SyncOperation.UPLOAD);
    }

    /**
     * Public method to delete a specific file
     */
    public void deleteFileManually(String filePath) {
        if (!isAuthenticated()) {
            logger.warn("Cannot delete file - user not authenticated");
            return;
        }
        queueFileSync(filePath, SyncOperation.DELETE);
    }
    
    /**
     * Queue file for upload (compatibility method for FileWatchService)
     */
    public void queueFileForUpload(Path filePath) {
        if (!isAuthenticated()) {
            logger.error("Cannot upload file - user not authenticated. Please login first: {}", filePath);
            return;
        }
        String relativePath = getRelativePath(filePath);
        queueFileSync(relativePath, SyncOperation.UPLOAD);
    }
    
    /**
     * Queue file for deletion (compatibility method for FileWatchService)
     * Process immediately to prevent race conditions with other clients
     */
    public void queueFileForDeletion(Path filePath) {
        if (!isAuthenticated()) {
            logger.error("Cannot delete file - user not authenticated. Please login first: {}", filePath);
            return;
        }
        
        String filePathStr = getRelativePath(filePath);
        logger.info("Processing immediate deletion for file: {}", filePathStr);
        
        // CRITICAL: Mark as deleted IMMEDIATELY to prevent re-download during race conditions
        markFileAsDeleted(filePathStr);
        
        // Queue for background processing 
        queueFileSync(filePathStr, SyncOperation.DELETE);
        
        // Also process immediately in a separate thread to notify server
        executorService.submit(() -> {
            try {
                deleteFileOnServer(filePathStr);
                logger.info("Immediate deletion completed for: {}", filePathStr);
            } catch (Exception e) {
                logger.error("Failed to process immediate deletion for: " + filePathStr, e);
                // If server deletion fails, we keep the local DELETED marker
                // This prevents the file from being re-downloaded
            }
        });
    }
    
    /**
     * Check if user is logged in (compatibility method for MainController)
     */
    public boolean isLoggedIn() {
        return isAuthenticated();
    }
    
    /**
     * Register a new user (compatibility method for MainController)
     */
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
    
    /**
     * Login user
     */
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
                    config.saveConfig();
                    
                    logger.info("Login successful for user: {}", username);
                    
                    // Initialize WebSocket sync after successful authentication
                    initializeWebSocketSync();
                    
                    // Perform initial directory scan to detect existing untracked files
                    executorService.schedule(this::performInitialDirectoryScan, 2, TimeUnit.SECONDS);
                    
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
    
    /**
     * Stop the sync service and cleanup resources
     */
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
    
    /**
     * Sync task representation
     */
    private static class SyncTask {
        final String filePath;
        final SyncOperation operation;
        
        SyncTask(String filePath, SyncOperation operation) {
            this.filePath = filePath;
            this.operation = operation;
        }
    }
    
    /**
     * Sync operation types
     */
    public enum SyncOperation {
        UPLOAD, DOWNLOAD, DELETE, CONFLICT_RESOLVE
    }

    /**
     * Upload external file by copying it to sync directory first
     */
    public void uploadExternalFile(Path externalFile, String targetRelativePath) throws IOException {
        if (!isAuthenticated()) {
            throw new IllegalStateException("Cannot upload file - user not authenticated");
        }
        
        // Validate external file
        if (!Files.exists(externalFile) || !Files.isRegularFile(externalFile)) {
            throw new IllegalArgumentException("Invalid file: " + externalFile);
        }
        
        // Clean and normalize the target path
        String cleanPath = targetRelativePath.trim();
        
        // Convert backslashes to forward slashes for consistent checking
        String normalizedPath = cleanPath.replace("\\", "/");
        
        // Remove any leading slashes
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        
        // Validate path (security check - no parent directory traversal)
        if (normalizedPath.contains("..") || normalizedPath.isEmpty()) {
            throw new IllegalArgumentException("Invalid target path: " + targetRelativePath);
        }
        
        // Use the normalized path for the rest of the operation
        targetRelativePath = normalizedPath;
        
        // Create target path in sync directory
        Path syncRoot = Paths.get(config.getLocalSyncPath()).toAbsolutePath().normalize();
        Path targetPath = syncRoot.resolve(targetRelativePath).normalize();
        
        // Ensure target is still within sync directory (security check)
        if (!targetPath.startsWith(syncRoot)) {
            throw new IllegalArgumentException("Target path outside sync directory: " + targetRelativePath + 
                " (resolved to: " + targetPath + ", sync root: " + syncRoot + ")");
        }
        
        // Ensure target directory exists
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }
        
        // Copy file to sync directory
        Files.copy(externalFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Queue for normal sync using relative path
        queueFileSync(targetRelativePath, SyncOperation.UPLOAD);
        
        logger.info("External file copied and queued for sync: {} -> {}", 
                   externalFile, targetRelativePath);
    }

    /**
     * Upload external file with automatic filename (convenience method)
     */
    public void uploadExternalFile(Path externalFile) throws IOException {
        String fileName = externalFile.getFileName().toString();
        uploadExternalFile(externalFile, fileName);
    }
    
    /**
     * Mark file as deleted with timestamp to avoid re-sync
     */
    private void markFileAsDeleted(String filePath) {
        try {
            // Mark as deleted in database
            databaseService.updateSyncStatus(filePath, "DELETED");
            
            logger.debug("Marked file as deleted: {}", filePath);
        } catch (Exception e) {
            logger.error("Error marking file as deleted: " + filePath, e);
        }
    }
    
    /**
     * Check if a file was recently deleted to avoid immediate re-sync
     */
    private boolean isRecentlyDeleted(String filePath) {
        try {
            String syncStatus = databaseService.getSyncStatus(filePath);
            return "DELETED".equals(syncStatus);
        } catch (Exception e) {
            logger.error("Error checking deletion status: " + filePath, e);
            return false;
        }
    }
    
    /**
     * Clean up old DELETED markers periodically
     * This prevents the database from growing indefinitely with old deletion markers
     */
    private void cleanupOldDeletedMarkers() {
        try {
            // Remove DELETED markers older than 1 hour
            // This gives enough time for all clients to sync the deletion
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            logger.debug("Cleaning up deleted markers older than: {}", cutoff);
            
            Map<String, String> trackedFiles = databaseService.getAllTrackedFiles();
            for (Map.Entry<String, String> entry : trackedFiles.entrySet()) {
                String filePath = entry.getKey();
                String syncStatus = entry.getValue();
                
                if ("DELETED".equals(syncStatus)) {
                    // Check if the file still doesn't exist on server
                    // and if enough time has passed, remove the marker
                    // For now, we'll implement a simple time-based cleanup
                    // In a more sophisticated version, you could store timestamps
                    
                    // Remove the DELETED marker after some time to prevent database bloat
                    // Only if file doesn't exist locally and isn't on server
                    Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
                    if (!Files.exists(localPath)) {
                        databaseService.removeFileRecord(filePath);
                        logger.debug("Cleaned up old DELETED marker for: {}", filePath);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error cleaning up old deleted markers", e);
        }
    }
    
    /**
     * Clear deletion status and re-upload a previously deleted file
     * This is useful when a user wants to upload a file with the same name/path that was previously deleted
     */
    public void clearDeletionAndUpload(String filePath) {
        if (!isAuthenticated()) {
            logger.warn("Cannot upload file - user not authenticated");
            return;
        }
        
        try {
            Path path = Paths.get(config.getLocalSyncPath(), filePath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                logger.warn("File does not exist: {}", filePath);
                return;
            }
            
            String relativePath = getRelativePath(path);
            
            // Clear any existing deletion status
            String currentSyncStatus = databaseService.getSyncStatus(relativePath);
            if ("DELETED".equals(currentSyncStatus)) {
                logger.info("Clearing deletion status for file: {}", relativePath);
                databaseService.updateSyncStatus(relativePath, "PENDING");
            }
            
            // Queue for upload
            queueFileSync(relativePath, SyncOperation.UPLOAD);
            
        } catch (Exception e) {
            logger.error("Error clearing deletion status and uploading file: {}", filePath, e);
        }
    }
    
    /**
     * Clear deletion status for a file without uploading
     * This allows the file to be synced normally if it appears again
     */
    public void clearDeletionStatus(String filePath) {
        try {
            String relativePath = getRelativePath(Paths.get(filePath));
            String currentSyncStatus = databaseService.getSyncStatus(relativePath);
            
            if ("DELETED".equals(currentSyncStatus)) {
                logger.info("Clearing deletion status for file: {}", relativePath);
                databaseService.updateSyncStatus(relativePath, "PENDING");
            } else {
                logger.info("File is not marked as deleted: {}", relativePath);
            }
            
        } catch (Exception e) {
            logger.error("Error clearing deletion status for file: {}", filePath, e);
        }
    }
    
    /**
     * Log all available files on server for debugging purposes
     */
    private void logAvailableFiles() {
        try {
            String listFilesUrl = config.getServerUrl() + "/files/";
            HttpGet get = new HttpGet(listFilesUrl);
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            logger.debug("Requesting file list from server for debugging: {}", listFilesUrl);
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getCode() == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    FileDto[] files = objectMapper.readValue(responseBody, FileDto[].class);
                    logger.info("Available files on server ({} total):", files.length);
                    for (FileDto file : files) {
                        logger.info("  - Path: '{}', ID: '{}', Name: '{}'", 
                            file.getFilePath(), file.getFileId(), file.getFileName());
                    }
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    logger.error("Failed to get file list for debugging. Status: {}, Response: {}", 
                        response.getCode(), responseBody);
                }
            }
        } catch (Exception e) {
            logger.error("Error logging available files for debugging", e);
        }
    }

    // WebSocket Event Handler Implementation
    @Override
    public void handleFileChangeEvent(SyncEventDto event) {
        logger.info("Received real-time file change: {} for {}", event.getEventType(), event.getFilePath());
        
        try {
            // Don't process events from our own client
            if (clientId.equals(event.getClientId())) {
                logger.debug("Ignoring event from our own client: {}", event.getClientId());
                return;
            }
            
            String eventType = event.getEventType();
            
            switch (eventType) {
                case "CREATE":
                case "MODIFY":
                    // Download the file that was created/modified
                    logger.info("Queuing download for real-time event: {}", event.getFilePath());
                    queueFileSync(event.getFilePath(), SyncOperation.DOWNLOAD);
                    break;
                case "DELETE":
                    // Mark the file as deleted locally  
                    handleRemoteFileDeletion(event.getFilePath());
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
            // Log the conflict and trigger UI-based resolution
            logger.info("Conflict resolution needed for: {} - checksum: {}", 
                       event.getFilePath(), event.getChecksum());
            
            // Queue the file for conflict resolution with UI integration
            queueFileSync(event.getFilePath(), SyncOperation.CONFLICT_RESOLVE);
            
        } catch (Exception e) {
            logger.error("Error handling conflict event: {}", event, e);
        }
    }
    
    @Override
    public void handleConnectionStatusChange(boolean connected) {
        webSocketConnected.set(connected);
        
        if (connected) {
            logger.info("WebSocket connected - switching to real-time sync mode");
            // No need to update auth token here - the connection was already established with the current token
            // The updateAuthToken method should only be called when the token actually changes
        } else {
            logger.warn("WebSocket disconnected - relying on polling mode");
        }
        
        // Adjust polling frequency based on WebSocket status
        adjustPollingFrequency();
    }
    
    // Helper methods for WebSocket event handling
    private void handleRemoteFileDeletion(String filePath) {
        try {
            Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
            
            if (Files.exists(localPath)) {
                Files.delete(localPath);
                logger.info("Deleted file from real-time event: {}", filePath);
            }
            
            // Mark as deleted in database to prevent re-download
            markFileAsDeleted(filePath);
            
        } catch (Exception e) {
            logger.error("Error handling remote file deletion: {}", filePath, e);
        }
    }
    
    private void adjustPollingFrequency() {
        // This would require restructuring the periodic sync scheduling
        // For now, just log the change
        int newInterval = webSocketConnected.get() ? POLLING_INTERVAL_CONNECTED : POLLING_INTERVAL_DISCONNECTED;
        logger.info("Adjusting polling frequency to {} seconds based on WebSocket status", newInterval);
    }
    
    /**
     * Initialize WebSocket client for real-time sync
     */
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
    
    /**
     * Download file content for conflict resolution without saving to disk
     */
    public FileDto downloadFileContent(String filePath) throws IOException {
        if (!isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        
        String fileId = findFileIdByPath(filePath);
        if (fileId == null) {
            throw new RuntimeException("File not found on server: " + filePath);
        }
        
        String downloadUrl = config.getServerUrl() + "/files/" + fileId + "/download";
        HttpGet get = new HttpGet(downloadUrl);
        get.setHeader("Authorization", "Bearer " + config.getToken());
        
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            if (response.getCode() == 200) {
                byte[] content = EntityUtils.toByteArray(response.getEntity());
                
                // Create FileDto with content
                FileDto fileDto = new FileDto();
                fileDto.setFileId(fileId);
                fileDto.setFilePath(filePath);
                fileDto.setContent(content);
                fileDto.setChecksum(calculateChecksum(content));
                fileDto.setFileSize((long) content.length);
                
                return fileDto;
            } else {
                throw new RuntimeException("Failed to download file: " + response.getCode());
            }
        }
    }

    /**
     * Resolve conflict implementation with UI integration
     */
    private void resolveConflictWithUI(String filePath) {
        logger.info("Starting UI-based conflict resolution for: {}", filePath);
        
        Platform.runLater(() -> {
            try {
                ConflictResolutionController.ConflictResolutionResult result = 
                    ConflictResolutionController.showConflictDialog(this, config, filePath);
                
                switch (result) {
                    case USE_LOCAL -> {
                        // Upload local version
                        uploadResolvedFile(filePath);
                        logger.info("Conflict resolved: using local version for {}", filePath);
                    }
                    case USE_SERVER -> {
                        // Download server version
                        queueFileSync(filePath, SyncOperation.DOWNLOAD);
                        logger.info("Conflict resolved: using server version for {}", filePath);
                    }
                    case USE_MERGED -> {
                        // Upload the merged version (already saved to local file)
                        uploadResolvedFile(filePath);
                        logger.info("Conflict resolved: using merged version for {}", filePath);
                    }
                    case CANCELLED -> {
                        logger.info("Conflict resolution cancelled for {}", filePath);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in conflict resolution UI for: {}", filePath, e);
                // Fallback to automatic resolution
                fallbackConflictResolution(filePath);
            }
        });
    }
    
    /**
     * Upload resolved conflict file to server
     */
    public void uploadResolvedFile(String filePath) {
        try {
            Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
            if (!Files.exists(localPath)) {
                logger.warn("Resolved file does not exist: {}", filePath);
                return;
            }
            
            // Upload the resolved file
            uploadFile(localPath.toString());
            logger.info("Successfully uploaded resolved file: {}", filePath);
            
        } catch (Exception e) {
            logger.error("Error uploading resolved file: {}", filePath, e);
        }
    }
    
    /**
     * Set the main controller for conflict notifications
     */
    public void setMainController(com.filesync.client.ui.MainController mainController) {
        if (conflictManager != null) {
            conflictManager.setMainController(mainController);
        }
    }
    
    /**
     * Get the conflict manager
     */
    public ConflictManager getConflictManager() {
        return conflictManager;
    }

    /**
     * Perform initial scan of sync directory to queue existing untracked files for upload
     * This ensures files that existed before the client started are uploaded
     */
    public void performInitialDirectoryScan() {
        if (!isAuthenticated()) {
            logger.debug("Skipping initial directory scan - user not authenticated");
            return;
        }
        
        logger.info("Starting initial directory scan for untracked files");
        
        try {
            Path syncRoot = Paths.get(config.getLocalSyncPath());
            if (!Files.exists(syncRoot) || !Files.isDirectory(syncRoot)) {
                logger.warn("Sync directory does not exist: {}", syncRoot);
                return;
            }
            
            logger.info("Scanning directory: {}", syncRoot);
            
            // Count total files found using atomic integers
            java.util.concurrent.atomic.AtomicInteger totalFiles = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger trackedFiles = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger untrackedFiles = new java.util.concurrent.atomic.AtomicInteger(0);
            
            // Walk through all files in sync directory
            Files.walk(syncRoot)
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    try {
                        String relativePath = getRelativePath(filePath);
                        logger.debug("Processing file: {} -> {}", filePath, relativePath);
                        
                        // Check if file is already tracked in database
                        String syncStatus = databaseService.getSyncStatus(relativePath);
                        
                        if (syncStatus == null) {
                            // File is not tracked - queue it for upload
                            logger.info("Found untracked file during initial scan: {}", relativePath);
                            queueFileSync(filePath.toString(), SyncOperation.UPLOAD);
                            untrackedFiles.incrementAndGet();
                        } else if ("DELETED".equals(syncStatus)) {
                            // File was previously deleted but now exists again - clear deletion and upload
                            logger.info("Found previously deleted file during initial scan: {}", relativePath);
                            clearDeletionAndUpload(filePath.toString());
                            untrackedFiles.incrementAndGet();
                        } else {
                            // File is already tracked
                            logger.debug("File already tracked during initial scan: {} (status: {})", relativePath, syncStatus);
                            trackedFiles.incrementAndGet();
                        }
                        totalFiles.incrementAndGet();
                    } catch (Exception e) {
                        logger.error("Error processing file during initial scan: {}", filePath, e);
                    }
                });
            
            logger.info("Initial directory scan completed - Total files: {}, Tracked: {}, Untracked: {}", 
                       totalFiles.get(), trackedFiles.get(), untrackedFiles.get());
            
        } catch (Exception e) {
            logger.error("Error during initial directory scan", e);
        }
    }
}
