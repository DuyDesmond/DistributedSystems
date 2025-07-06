package com.filesync.server.entity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/**
 * Entity for tracking chunked upload sessions
 */
@Entity
@Table(name = "chunk_upload_sessions", indexes = {
    @Index(name = "idx_chunk_sessions_user_id", columnList = "user_id"),
    @Index(name = "idx_chunk_sessions_file_id", columnList = "file_id"),
    @Index(name = "idx_chunk_sessions_created", columnList = "created_at")
})
public class ChunkUploadSessionEntity {
    
    @Id
    @Column(name = "session_id")
    @JsonProperty("session_id")
    private String sessionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    private UserEntity user;
    
    @Column(name = "file_id", nullable = false)
    @JsonProperty("file_id")
    private String fileId;
    
    @Column(name = "file_path", nullable = false)
    @JsonProperty("file_path")
    private String filePath;
    
    @Column(name = "total_chunks", nullable = false)
    @JsonProperty("total_chunks")
    private Integer totalChunks;
    
    @Column(name = "received_chunks", nullable = false)
    @JsonProperty("received_chunks")
    private Integer receivedChunks = 0;
    
    @Column(name = "total_file_size")
    @JsonProperty("total_file_size")
    private Long totalFileSize;
    
    @Column(name = "received_size")
    @JsonProperty("received_size")
    private Long receivedSize = 0L;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @JsonProperty("status")
    private UploadStatus status = UploadStatus.IN_PROGRESS;
    
    @ElementCollection
    @CollectionTable(name = "chunk_upload_progress", 
                    joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyColumn(name = "chunk_index")
    @Column(name = "chunk_checksum")
    private Map<Integer, String> receivedChunkChecksums = new HashMap<>();
    
    @Column(name = "storage_path")
    @JsonProperty("storage_path")
    private String storagePath;
    
    @Column(name = "final_checksum")
    @JsonProperty("final_checksum")
    private String finalChecksum;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "completed_at")
    @JsonProperty("completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "expires_at", nullable = false)
    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "error_message")
    @JsonProperty("error_message")
    private String errorMessage;
    
    // Constructors
    public ChunkUploadSessionEntity() {}
    
    public ChunkUploadSessionEntity(String sessionId, UserEntity user, String fileId, 
                                   String filePath, Integer totalChunks, Long totalFileSize) {
        this.sessionId = sessionId;
        this.user = user;
        this.fileId = fileId;
        this.filePath = filePath;
        this.totalChunks = totalChunks;
        this.totalFileSize = totalFileSize;
        this.expiresAt = LocalDateTime.now().plusHours(24); // 24 hour expiry
    }
    
    // Methods
    public void addReceivedChunk(Integer chunkIndex, String checksum, Long chunkSize) {
        if (!receivedChunkChecksums.containsKey(chunkIndex)) {
            receivedChunkChecksums.put(chunkIndex, checksum);
            receivedChunks++;
            receivedSize += chunkSize;
        }
    }
    
    public boolean isComplete() {
        return receivedChunks.equals(totalChunks);
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    @JsonProperty("progress")
    public double getProgress() {
        if (totalChunks == null || totalChunks == 0) return 0.0;
        return (double) receivedChunks / totalChunks * 100.0;
    }
    
    public void markCompleted(String finalChecksum, String storagePath) {
        this.status = UploadStatus.COMPLETED;
        this.finalChecksum = finalChecksum;
        this.storagePath = storagePath;
        this.completedAt = LocalDateTime.now();
    }
    
    public void markFailed(String errorMessage) {
        this.status = UploadStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public UserEntity getUser() {
        return user;
    }
    
    public void setUser(UserEntity user) {
        this.user = user;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public Integer getTotalChunks() {
        return totalChunks;
    }
    
    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }
    
    public Integer getReceivedChunks() {
        return receivedChunks;
    }
    
    public void setReceivedChunks(Integer receivedChunks) {
        this.receivedChunks = receivedChunks;
    }
    
    public Long getTotalFileSize() {
        return totalFileSize;
    }
    
    public void setTotalFileSize(Long totalFileSize) {
        this.totalFileSize = totalFileSize;
    }
    
    public Long getReceivedSize() {
        return receivedSize;
    }
    
    public void setReceivedSize(Long receivedSize) {
        this.receivedSize = receivedSize;
    }
    
    public UploadStatus getStatus() {
        return status;
    }
    
    public void setStatus(UploadStatus status) {
        this.status = status;
    }
    
    public Map<Integer, String> getReceivedChunkChecksums() {
        return receivedChunkChecksums;
    }
    
    public void setReceivedChunkChecksums(Map<Integer, String> receivedChunkChecksums) {
        this.receivedChunkChecksums = receivedChunkChecksums;
    }
    
    public String getStoragePath() {
        return storagePath;
    }
    
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
    
    public String getFinalChecksum() {
        return finalChecksum;
    }
    
    public void setFinalChecksum(String finalChecksum) {
        this.finalChecksum = finalChecksum;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public enum UploadStatus {
        IN_PROGRESS, COMPLETED, FAILED, EXPIRED
    }
}
