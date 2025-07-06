package com.filesync.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.filesync.common.dto.FileChunkDto;
import com.filesync.common.util.ChunkingUtil;
import com.filesync.server.entity.ChunkUploadSessionEntity;
import com.filesync.server.entity.FileEntity;
import com.filesync.server.entity.UserEntity;
import com.filesync.server.repository.ChunkUploadSessionRepository;
import com.filesync.server.repository.FileRepository;
import com.filesync.server.repository.UserRepository;
import com.filesync.server.util.StoragePathUtil;

/**
 * Service for handling chunked file uploads
 */
@Service
public class ChunkService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChunkService.class);
    
    @Autowired
    private ChunkUploadSessionRepository sessionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private FileRepository fileRepository;
    
    @Value("${filesync.storage.base-path:./storage}")
    private String storageBasePath;
    
    @Value("${filesync.chunking.max-concurrent-sessions:10}")
    private int maxConcurrentSessions;
    
    @Value("${filesync.chunking.session-timeout-hours:24}")
    private int sessionTimeoutHours;
    
    // In-memory cache for active chunk data
    private final Map<String, Map<Integer, byte[]>> chunkCache = new ConcurrentHashMap<>();
    
    /**
     * Initiate a chunked upload session
     */
    @Transactional
    public ChunkUploadSessionEntity initiateChunkedUpload(String username, String fileId, 
                                                         String filePath, Integer totalChunks, 
                                                         Long totalFileSize) {
        // Validate input parameters
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("FileId cannot be null or empty");
        }
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("FilePath cannot be null or empty");
        }
        if (totalChunks == null || totalChunks <= 0) {
            throw new IllegalArgumentException("TotalChunks must be a positive integer");
        }
        if (totalFileSize == null || totalFileSize <= 0) {
            throw new IllegalArgumentException("TotalFileSize must be a positive number");
        }
        
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        // Check for concurrent session limits
        long activeSessions = sessionRepository.countActiveSessionsByUser(user, LocalDateTime.now());
        if (activeSessions >= maxConcurrentSessions) {
            throw new RuntimeException("Too many concurrent upload sessions. Limit: " + maxConcurrentSessions);
        }
        
        String sessionId = UUID.randomUUID().toString();
        
        ChunkUploadSessionEntity session = new ChunkUploadSessionEntity(
            sessionId, user, fileId, filePath, totalChunks, totalFileSize
        );
        
        session.setExpiresAt(LocalDateTime.now().plusHours(sessionTimeoutHours));
        session = sessionRepository.save(session);
        
        // Initialize chunk cache for this session
        chunkCache.put(sessionId, new ConcurrentHashMap<>());
        
        logger.info("Initiated chunked upload session {} for user {} - file: {}, chunks: {}, size: {}", 
            sessionId, username, filePath, totalChunks, totalFileSize);
        
        return session;
    }
    
    /**
     * Upload a single chunk
     */
    @Transactional
    public ChunkUploadSessionEntity uploadChunk(String username, String sessionId, 
                                               Integer chunkIndex, MultipartFile chunkData) 
                                               throws IOException {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        ChunkUploadSessionEntity session = sessionRepository.findBySessionIdAndUser(sessionId, user)
            .orElseThrow(() -> new RuntimeException("Upload session not found"));
        
        if (session.isExpired()) {
            session.markFailed("Session expired");
            sessionRepository.save(session);
            chunkCache.remove(sessionId);
            throw new RuntimeException("Upload session expired");
        }
        
        if (session.getStatus() != ChunkUploadSessionEntity.UploadStatus.IN_PROGRESS) {
            throw new RuntimeException("Upload session is not in progress");
        }
        
        // Validate chunk index
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new RuntimeException("Invalid chunk index: " + chunkIndex);
        }
        
        // Check if chunk already received
        if (session.getReceivedChunkChecksums().containsKey(chunkIndex)) {
            logger.warn("Chunk {} already received for session {}", chunkIndex, sessionId);
            return session;
        }
        
        byte[] chunkBytes = chunkData.getBytes();
        String chunkChecksum = ChunkingUtil.calculateChecksum(chunkBytes);
        
        // Store chunk in cache
        Map<Integer, byte[]> sessionChunks = chunkCache.get(sessionId);
        if (sessionChunks == null) {
            sessionChunks = new ConcurrentHashMap<>();
            chunkCache.put(sessionId, sessionChunks);
        }
        sessionChunks.put(chunkIndex, chunkBytes);
        
        // Update session
        session.addReceivedChunk(chunkIndex, chunkChecksum, (long) chunkBytes.length);
        session = sessionRepository.save(session);
        
        logger.debug("Received chunk {}/{} for session {} ({}% complete)", 
            chunkIndex + 1, session.getTotalChunks(), sessionId, 
            Math.round(session.getProgress()));
        
        // Check if upload is complete
        if (session.isComplete()) {
            completeChunkedUpload(session);
        }
        
        return session;
    }
    
    /**
     * Complete chunked upload by assembling all chunks
     */
    @Transactional
    protected void completeChunkedUpload(ChunkUploadSessionEntity session) throws IOException {
        String sessionId = session.getSessionId();
        
        try {
            // Get all chunks from cache
            Map<Integer, byte[]> sessionChunks = chunkCache.get(sessionId);
            if (sessionChunks == null || sessionChunks.size() != session.getTotalChunks()) {
                throw new IOException("Missing chunks in cache");
            }
            
            // Create FileChunkDto list for assembly
            List<FileChunkDto> chunks = new ArrayList<>();
            for (int i = 0; i < session.getTotalChunks(); i++) {
                byte[] chunkData = sessionChunks.get(i);
                if (chunkData == null) {
                    throw new IOException("Missing chunk " + i);
                }
                
                FileChunkDto chunk = new FileChunkDto();
                chunk.setChunkIndex(i);
                chunk.setTotalChunks(session.getTotalChunks());
                chunk.setChunkData(chunkData);
                chunk.setChunkChecksum(session.getReceivedChunkChecksums().get(i));
                chunk.setIsLastChunk(i == session.getTotalChunks() - 1);
                chunks.add(chunk);
            }
            
            // Validate and assemble chunks
            ChunkingUtil.validateChunkSequence(chunks);
            byte[] completeFileData = ChunkingUtil.assembleChunks(chunks);
            
            // Verify total file size
            if (completeFileData.length != session.getTotalFileSize()) {
                throw new IOException("Assembled file size mismatch");
            }
            
            // Calculate final checksum
            String finalChecksum = ChunkingUtil.calculateChecksum(completeFileData);
            
            // Save complete file
            String storagePath = createStoragePath(session.getUser().getUserId(), session.getFileId());
            saveFileToStorage(completeFileData, storagePath);
            
            // Update or create file entity
            updateFileEntity(session, finalChecksum, storagePath, completeFileData.length);
            
            // Mark session as completed
            session.markCompleted(finalChecksum, storagePath);
            sessionRepository.save(session);
            
            // Clean up cache
            chunkCache.remove(sessionId);
            
            logger.info("Completed chunked upload for session {} - file: {}, size: {} bytes", 
                sessionId, session.getFilePath(), completeFileData.length);
            
        } catch (IOException | RuntimeException e) {
            session.markFailed("Failed to complete upload: " + e.getMessage());
            sessionRepository.save(session);
            chunkCache.remove(sessionId);
            logger.error("Failed to complete chunked upload for session {}: {}", sessionId, e.getMessage(), e);
            throw new IOException("Failed to complete chunked upload", e);
        }
    }
    
    /**
     * Get upload session status
     */
    public ChunkUploadSessionEntity getUploadStatus(String username, String sessionId) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return sessionRepository.findBySessionIdAndUser(sessionId, user)
            .orElseThrow(() -> new RuntimeException("Upload session not found"));
    }
    
    /**
     * Cancel an upload session
     */
    @Transactional
    public void cancelUploadSession(String username, String sessionId) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        ChunkUploadSessionEntity session = sessionRepository.findBySessionIdAndUser(sessionId, user)
            .orElseThrow(() -> new RuntimeException("Upload session not found"));
        
        session.markFailed("Cancelled by user");
        sessionRepository.save(session);
        chunkCache.remove(sessionId);
        
        logger.info("Cancelled upload session {} for user {}", sessionId, username);
    }
    
    /**
     * Get active upload sessions for a user
     */
    public List<ChunkUploadSessionEntity> getActiveUploadSessions(String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return sessionRepository.findActiveSessionsByUser(user, LocalDateTime.now());
    }
    
    /**
     * Cleanup expired and old sessions
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        
        // Mark expired sessions
        List<ChunkUploadSessionEntity> expiredSessions = sessionRepository.findExpiredSessions(now);
        for (ChunkUploadSessionEntity session : expiredSessions) {
            if (session.getStatus() == ChunkUploadSessionEntity.UploadStatus.IN_PROGRESS) {
                session.setStatus(ChunkUploadSessionEntity.UploadStatus.EXPIRED);
                sessionRepository.save(session);
                chunkCache.remove(session.getSessionId());
            }
        }
        
        // Delete old completed sessions (older than 7 days)
        LocalDateTime cutoff = now.minusDays(7);
        int deletedCompleted = sessionRepository.deleteOldCompletedSessions(cutoff);
        
        // Delete failed/expired sessions (older than 1 day)
        LocalDateTime cleanupCutoff = now.minusDays(1);
        int deletedExpired = sessionRepository.deleteExpiredSessions(cleanupCutoff);
        
        // Clean up cache for removed sessions
        chunkCache.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            return !sessionRepository.existsById(sessionId);
        });
        
        if (deletedCompleted > 0 || deletedExpired > 0) {
            logger.info("Cleaned up chunk upload sessions - completed: {}, expired: {}", 
                deletedCompleted, deletedExpired);
        }
    }
    
    private void updateFileEntity(ChunkUploadSessionEntity session, String checksum, 
                                 String storagePath, long fileSize) {
        UserEntity user = session.getUser();
        
        // Check if file already exists
        Optional<FileEntity> existingFile = fileRepository.findByUserAndFilePath(user, session.getFilePath());
        
        FileEntity fileEntity;
        if (existingFile.isPresent()) {
            // Update existing file - FIXED: Don't increment version vector for simple updates
            // Let the SyncService handle version vector management during sync operations
            fileEntity = existingFile.get();
            fileEntity.setFileSize(fileSize);
            fileEntity.setChecksum(checksum);
            fileEntity.setModifiedAt(LocalDateTime.now());
            fileEntity.setStoragePath(storagePath);
            // Clear any conflict status since we're successfully uploading
            fileEntity.setConflictStatus(null);
        } else {
            // Create new file
            String fileName = Paths.get(session.getFilePath()).getFileName().toString();
            fileEntity = new FileEntity(session.getFileId(), user, session.getFilePath(), fileName);
            fileEntity.setFileSize(fileSize);
            fileEntity.setChecksum(checksum);
            fileEntity.getCurrentVersionVector().increment(user.getUserId());
            fileEntity.setStoragePath(storagePath);
        }
        
        fileRepository.save(fileEntity);
    }
    
    private String createStoragePath(String userId, String fileId) {
        return StoragePathUtil.createStoragePath(storageBasePath, userId, fileId);
    }
    
    private void saveFileToStorage(byte[] data, String storagePath) throws IOException {
        Path path = Paths.get(storagePath);
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    }
}
