package com.filesync.client.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
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
import com.filesync.common.model.VersionVector;

/**
 * Enhanced Synchronization Service with version vector support
 */
public class EnhancedSyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedSyncService.class);
    
    private final ClientConfig config;
    private final DatabaseService databaseService;
    private final ScheduledExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final String clientId;
    
    private final BlockingQueue<SyncTask> syncQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    
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
        this.clientId = generateClientId();
        
        startSyncProcessor();
        schedulePeriodicSync();
    }
    
    /**
     * Generate unique client ID
     */
    private String generateClientId() {
        String existingId = databaseService.getConfig("client_id", null);
        if (existingId == null) {
            existingId = UUID.randomUUID().toString();
            databaseService.setConfig("client_id", existingId);
        }
        return existingId;
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
        logger.debug("Queued file for sync: {} ({})", filePath, operation);
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
        return config.getToken() != null && !config.getToken().isEmpty();
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
     * Upload file implementation
     */
    private void uploadFile(String filePath) {
        logger.info("Uploading file: {}", filePath);
        
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                logger.warn("File does not exist or is not a regular file: {}", filePath);
                return;
            }
            
            // Calculate file info
            byte[] fileData = Files.readAllBytes(path);
            String checksum = calculateChecksum(fileData);
            long fileSize = Files.size(path);
            String relativePath = getRelativePath(path);
            
            // Get or create version vector
            VersionVector versionVector = databaseService.getFileVersionVectorByPath(relativePath);
            if (versionVector == null) {
                versionVector = new VersionVector();
            }
            versionVector.increment(clientId);
            
            // Store version vector locally first
            String fileId = UUID.randomUUID().toString();
            databaseService.storeFileVersionVector(fileId, relativePath, versionVector, 
                LocalDateTime.now(), fileSize, checksum);
            
            // Upload to server
            HttpPost post = new HttpPost(config.getServerUrl() + "/files/upload");
            post.setHeader("Authorization", "Bearer " + config.getToken());
            
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", fileData, ContentType.APPLICATION_OCTET_STREAM, 
                path.getFileName().toString());
            builder.addTextBody("path", relativePath, ContentType.TEXT_PLAIN);
            builder.addTextBody("clientId", clientId, ContentType.TEXT_PLAIN);
            builder.addTextBody("versionVector", versionVector.toJson(), ContentType.TEXT_PLAIN);
            builder.addTextBody("checksum", checksum, ContentType.TEXT_PLAIN);
            
            post.setEntity(builder.build());
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                if (response.getCode() == 200) {
                    databaseService.updateSyncStatus(relativePath, "SYNCED");
                    logger.info("File uploaded successfully: {}", relativePath);
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    logger.error("File upload failed: {} - {}", relativePath, responseBody);
                    throw new RuntimeException("Upload failed: " + responseBody);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error uploading file: {}", filePath, e);
            // Retry logic could be added here
        }
    }

    /**
     * Download file implementation
     */
    private void downloadFile(String filePath) {
        logger.info("Downloading file: {}", filePath);
        
        try {
            String fileId = findFileIdByPath(filePath);
            if (fileId == null) {
                logger.warn("Could not find file ID for path: {}", filePath);
                return;
            }
            
            HttpGet get = new HttpGet(config.getServerUrl() + "/files/" + fileId + "/download");
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getCode() == 200) {
                    Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
                    Files.createDirectories(localPath.getParent());
                    Files.copy(response.getEntity().getContent(), localPath);
                    
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
                    String responseBody = EntityUtils.toString(response.getEntity());
                    logger.error("File download failed: {} - {}", filePath, responseBody);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error downloading file: {}", filePath, e);
        }
    }

    /**
     * Delete file implementation
     */
    private void deleteFile(String filePath) {
        logger.info("Deleting file: {}", filePath);
        
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
                    
                    // Update local database
                    databaseService.updateSyncStatus(filePath, "DELETED");
                    
                    logger.info("File deleted successfully: {}", filePath);
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    logger.error("File deletion failed: {} - {}", filePath, responseBody);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error deleting file: {}", filePath, e);
        }
    }

    /**
     * Resolve conflict implementation
     */
    private void resolveConflict(String filePath) {
        logger.info("Resolving conflict for: {}", filePath);
        
        try {
            // For now, implement a simple Last-Write-Wins strategy
            // In a full implementation, this would involve user interaction
            
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
            logger.error("Error resolving conflict for: {}", filePath, e);
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
                    
                    for (FileDto serverFile : serverFiles) {
                        processServerFile(serverFile);
                    }
                    
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
     * Process individual server file for sync
     */
    private void processServerFile(FileDto serverFile) {
        try {
            String filePath = serverFile.getFilePath();
            Path localPath = Paths.get(config.getLocalSyncPath(), filePath);
            
            // Get local version vector
            VersionVector localVector = databaseService.getFileVersionVectorByPath(filePath);
            VersionVector serverVector = serverFile.getVersionVector();
            
            if (localVector == null) {
                // File doesn't exist locally - download it
                queueFileSync(filePath, SyncOperation.DOWNLOAD);
            } else if (serverVector != null) {
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
            HttpGet get = new HttpGet(config.getServerUrl() + "/files/");
            get.setHeader("Authorization", "Bearer " + config.getToken());
            
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getCode() == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    FileDto[] files = objectMapper.readValue(responseBody, FileDto[].class);
                    
                    for (FileDto file : files) {
                        if (file.getFilePath().equals(relativePath)) {
                            return file.getFileId();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error finding file ID for path: {}", relativePath, e);
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
            logger.warn("Cannot upload file - user not authenticated");
            return;
        }
        queueFileSync(filePath.toString(), SyncOperation.UPLOAD);
    }
    
    /**
     * Queue file for deletion (compatibility method for FileWatchService)
     */
    public void queueFileForDeletion(Path filePath) {
        if (!isAuthenticated()) {
            logger.warn("Cannot delete file - user not authenticated");
            return;
        }
        queueFileSync(filePath.toString(), SyncOperation.DELETE);
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
     * Stop the sync service
     */
    public void stop() {
        running = false;
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
        
        // Validate target path (security check - no parent directory traversal)
        if (targetRelativePath.contains("..") || targetRelativePath.startsWith("/") || targetRelativePath.startsWith("\\")) {
            throw new IllegalArgumentException("Invalid target path: " + targetRelativePath);
        }
        
        // Create target path in sync directory
        Path syncRoot = Paths.get(config.getLocalSyncPath());
        Path targetPath = syncRoot.resolve(targetRelativePath).normalize();
        
        // Ensure target is still within sync directory (security check)
        if (!targetPath.startsWith(syncRoot)) {
            throw new IllegalArgumentException("Target path outside sync directory: " + targetRelativePath);
        }
        
        // Ensure target directory exists
        Files.createDirectories(targetPath.getParent());
        
        // Copy file to sync directory
        Files.copy(externalFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Queue for normal sync
        queueFileSync(targetPath.toString(), SyncOperation.UPLOAD);
        
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
}
