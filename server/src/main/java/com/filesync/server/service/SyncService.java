package com.filesync.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filesync.common.dto.SyncEventDto;
import com.filesync.common.model.VersionVector;
import com.filesync.server.controller.SyncWebSocketController;
import com.filesync.server.entity.FileEntity;
import com.filesync.server.entity.FileVersionEntity;
import com.filesync.server.entity.SyncEventEntity;
import com.filesync.server.entity.UserEntity;
import com.filesync.server.repository.FileRepository;
import com.filesync.server.repository.FileVersionRepository;
import com.filesync.server.repository.SyncEventRepository;
import com.filesync.server.repository.UserRepository;
import com.filesync.server.util.StoragePathUtil;

/**
 * Service for handling advanced synchronization logic with version vectors
 */
@Service
public class SyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    
    @Autowired
    private FileRepository fileRepository;
    
    @Autowired
    private FileVersionRepository fileVersionRepository;
    
    @Autowired
    private SyncEventRepository syncEventRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SyncWebSocketController webSocketController;
    
    @Value("${filesync.storage.base-path:/app/storage}")
    private String storageBasePath;
    
    /**
     * Process file sync request with conflict detection
     */
    @Transactional
    public SyncResult syncFile(String username, String filePath, VersionVector clientVersionVector, 
                              String clientId, String checksum, long fileSize) {
        
        logger.debug("Processing sync for user: {}, file: {}, client: {}", username, filePath, clientId);
        
        try {
            UserEntity user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));
            
            Optional<FileEntity> existingFileOpt = fileRepository.findByUserAndFilePath(user, filePath);
            
            if (existingFileOpt.isEmpty()) {
                // New file - create initial version
                return handleNewFile(user, filePath, clientVersionVector, clientId, checksum, fileSize);
            } else {
                // Existing file - check for conflicts
                return handleExistingFile(existingFileOpt.get(), clientVersionVector, clientId, checksum, fileSize);
            }
            
        } catch (Exception e) {
            logger.error("Error processing file sync", e);
            return SyncResult.error("Sync processing failed: " + e.getMessage());
        }
    }
    
    /**
     * Handle new file creation
     */
    private SyncResult handleNewFile(UserEntity user, String filePath, VersionVector clientVersionVector, 
                                   String clientId, String checksum, long fileSize) {
        
        // Create new file entity
        FileEntity file = new FileEntity();
        file.setUser(user);
        file.setFilePath(filePath);
        file.setFileName(extractFileName(filePath));
        file.setFileSize(fileSize);
        file.setChecksum(checksum);
        file.setSyncStatus("SYNCED");
        file.setConflictStatus("NONE");
        
        // Initialize version vector with client version
        VersionVector initialVector = new VersionVector();
        initialVector.increment(clientId);
        file.setCurrentVersionVector(initialVector);
        
        file = fileRepository.save(file);
        
        // Create first version record
        FileVersionEntity version = new FileVersionEntity(file, 1, checksum, 
                                                          generateStoragePath(file), fileSize, true);
        version.setVersionVector(initialVector);
        version.setCreatedByClient(clientId);
        fileVersionRepository.save(version);
        
        // Create sync event
        createSyncEvent(user, file, SyncEventEntity.EventType.CREATE, clientId, checksum, fileSize);
        
        // Notify other clients
        notifyOtherClients(user.getUsername(), file, SyncEventEntity.EventType.CREATE, clientId);
        
        logger.info("Created new file: {} for user: {}", filePath, user.getUsername());
        return SyncResult.success("File created successfully");
    }
    
    /**
     * Handle existing file sync with conflict detection
     */
    private SyncResult handleExistingFile(FileEntity file, VersionVector clientVersionVector, 
                                        String clientId, String checksum, long fileSize) {
        
        VersionVector serverVersionVector = file.getCurrentVersionVector();
        
        if (serverVersionVector == null) {
            serverVersionVector = new VersionVector();
        }
        
        // Check for conflicts using version vector comparison
        if (clientVersionVector.isConcurrentWith(serverVersionVector)) {
            // Conflict detected
            return handleConflict(file, clientVersionVector, serverVersionVector, clientId, checksum, fileSize);
        } else if (clientVersionVector.dominates(serverVersionVector)) {
            // Client version is newer - update server
            return updateServerFile(file, clientVersionVector, clientId, checksum, fileSize);
        } else if (serverVersionVector.dominates(clientVersionVector)) {
            // Server version is newer - client should update
            return SyncResult.clientShouldUpdate("Server has newer version");
        } else {
            // Versions are equal - no sync needed
            return SyncResult.success("File already up to date");
        }
    }
    
    /**
     * Handle conflict situation
     */
    private SyncResult handleConflict(FileEntity file, VersionVector clientVersionVector, 
                                    VersionVector serverVersionVector, String clientId, 
                                    String checksum, long fileSize) {
        
        logger.warn("Conflict detected for file: {} between client: {} and server", file.getFilePath(), clientId);
        
        // Update conflict status
        file.setConflictStatus("CONFLICT");
        
        // Create conflict version
        int nextVersionNumber = getNextVersionNumber(file);
        FileVersionEntity conflictVersion = new FileVersionEntity(file, nextVersionNumber, checksum, 
                                                                  generateConflictStoragePath(file, clientId), 
                                                                  fileSize, false);
        conflictVersion.setVersionVector(clientVersionVector);
        conflictVersion.setCreatedByClient(clientId);
        fileVersionRepository.save(conflictVersion);
        
        // Merge version vectors
        VersionVector mergedVector = serverVersionVector.merge(clientVersionVector);
        mergedVector.increment("server"); // Increment server version for conflict resolution
        file.setCurrentVersionVector(mergedVector);
        fileRepository.save(file);
        
        // Create conflict sync event
        createSyncEvent(file.getUser(), file, SyncEventEntity.EventType.MODIFY, clientId, checksum, fileSize);
        
        // Notify user about conflict
        SyncEventDto conflictNotification = createConflictNotification(file, clientId);
        webSocketController.notifyUserConflict(file.getUser().getUsername(), conflictNotification);
        
        return SyncResult.conflict("Conflict detected - manual resolution required", conflictVersion.getVersionId());
    }
    
    /**
     * Update server file with client version
     */
    private SyncResult updateServerFile(FileEntity file, VersionVector clientVersionVector, 
                                      String clientId, String checksum, long fileSize) {
        
        // Mark current version as non-current
        fileVersionRepository.markAllVersionsAsNonCurrent(file);
        
        // Create new version
        int nextVersionNumber = getNextVersionNumber(file);
        FileVersionEntity newVersion = new FileVersionEntity(file, nextVersionNumber, checksum, 
                                                            generateStoragePath(file), fileSize, true);
        newVersion.setVersionVector(clientVersionVector);
        newVersion.setCreatedByClient(clientId);
        fileVersionRepository.save(newVersion);
        
        // Update file metadata
        file.setFileSize(fileSize);
        file.setChecksum(checksum);
        file.setCurrentVersionVector(clientVersionVector);
        file.setSyncStatus("SYNCED");
        file.setConflictStatus("NONE");
        fileRepository.save(file);
        
        // Create sync event
        createSyncEvent(file.getUser(), file, SyncEventEntity.EventType.MODIFY, clientId, checksum, fileSize);
        
        // Notify other clients
        notifyOtherClients(file.getUser().getUsername(), file, SyncEventEntity.EventType.MODIFY, clientId);
        
        logger.info("Updated file: {} for user: {} from client: {}", 
                   file.getFilePath(), file.getUser().getUsername(), clientId);
        return SyncResult.success("File updated successfully");
    }
    
    /**
     * Get sync events for a user since last sync
     */
    public List<SyncEventDto> getSyncEventsSince(String username, LocalDateTime since) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        List<SyncEventEntity> events = syncEventRepository.findByUserSinceLastSync(user, since);
        
        return events.stream()
                .map(this::convertToDto)
                .toList();
    }
    
    /**
     * Create sync event record
     */
    private void createSyncEvent(UserEntity user, FileEntity file, SyncEventEntity.EventType eventType, 
                               String clientId, String checksum, Long fileSize) {
        SyncEventEntity syncEvent = new SyncEventEntity(user, file, eventType, clientId);
        syncEvent.setFilePath(file.getFilePath());
        syncEvent.setChecksum(checksum);
        syncEvent.setFileSize(fileSize);
        syncEvent.setSyncStatus(SyncEventEntity.SyncStatus.COMPLETED);
        
        syncEventRepository.save(syncEvent);
    }
    
    /**
     * Notify other clients about file changes
     */
    private void notifyOtherClients(String username, FileEntity file, SyncEventEntity.EventType eventType, String excludeClientId) {
        SyncEventDto notification = new SyncEventDto();
        notification.setEventType(eventType.name());
        notification.setFilePath(file.getFilePath());
        notification.setChecksum(file.getChecksum());
        notification.setFileSize(file.getFileSize());
        notification.setTimestamp(LocalDateTime.now());
        notification.setClientId("server");
        
        webSocketController.notifyUserFileChange(username, notification);
    }
    
    /**
     * Create conflict notification
     */
    private SyncEventDto createConflictNotification(FileEntity file, String clientId) {
        SyncEventDto conflict = new SyncEventDto();
        conflict.setEventType("CONFLICT");
        conflict.setFilePath(file.getFilePath());
        conflict.setChecksum(file.getChecksum());
        conflict.setFileSize(file.getFileSize());
        conflict.setTimestamp(LocalDateTime.now());
        conflict.setClientId(clientId);
        
        return conflict;
    }
    
    /**
     * Get next version number for file
     */
    private int getNextVersionNumber(FileEntity file) {
        return fileVersionRepository.findMaxVersionNumberByFile(file).orElse(0) + 1;
    }
    
    /**
     * Generate storage path for file
     */
    private String generateStoragePath(FileEntity file) {
        return StoragePathUtil.createStoragePath(storageBasePath, file.getUser().getUserId(), file.getFileId());
    }
    
    /**
     * Generate conflict storage path
     */
    private String generateConflictStoragePath(FileEntity file, String clientId) {
        return StoragePathUtil.createConflictStoragePath(storageBasePath, file.getUser().getUserId(), 
                                                       file.getFileId(), clientId, LocalDateTime.now());
    }
    
    /**
     * Extract filename from path
     */
    private String extractFileName(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        int lastBackslash = filePath.lastIndexOf('\\');
        int lastSeparator = Math.max(lastSlash, lastBackslash);
        
        return lastSeparator >= 0 ? filePath.substring(lastSeparator + 1) : filePath;
    }
    
    /**
     * Convert SyncEventEntity to DTO
     */
    private SyncEventDto convertToDto(SyncEventEntity entity) {
        SyncEventDto dto = new SyncEventDto();
        dto.setEventType(entity.getEventType().name());
        dto.setFilePath(entity.getFilePath());
        dto.setTimestamp(entity.getTimestamp());
        dto.setClientId(entity.getClientId());
        dto.setFileSize(entity.getFileSize());
        dto.setChecksum(entity.getChecksum());
        return dto;
    }
    
    /**
     * Sync result wrapper
     */
    public static class SyncResult {
        private final boolean success;
        private final String message;
        private final SyncResultType type;
        private final String conflictVersionId;
        
        private SyncResult(boolean success, String message, SyncResultType type, String conflictVersionId) {
            this.success = success;
            this.message = message;
            this.type = type;
            this.conflictVersionId = conflictVersionId;
        }
        
        public static SyncResult success(String message) {
            return new SyncResult(true, message, SyncResultType.SUCCESS, null);
        }
        
        public static SyncResult conflict(String message, String conflictVersionId) {
            return new SyncResult(false, message, SyncResultType.CONFLICT, conflictVersionId);
        }
        
        public static SyncResult clientShouldUpdate(String message) {
            return new SyncResult(false, message, SyncResultType.CLIENT_SHOULD_UPDATE, null);
        }
        
        public static SyncResult error(String message) {
            return new SyncResult(false, message, SyncResultType.ERROR, null);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public SyncResultType getType() { return type; }
        public String getConflictVersionId() { return conflictVersionId; }
        
        public enum SyncResultType {
            SUCCESS, CONFLICT, CLIENT_SHOULD_UPDATE, ERROR
        }
    }
}
