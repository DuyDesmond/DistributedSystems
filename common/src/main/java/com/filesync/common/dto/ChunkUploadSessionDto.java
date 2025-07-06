package com.filesync.common.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for chunk upload session status
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChunkUploadSessionDto {
    
    @JsonProperty("session_id")
    private String sessionId;
    
    @JsonProperty("file_id")
    private String fileId;
    
    @JsonProperty("file_path")
    private String filePath;
    
    @JsonProperty("total_chunks")
    private Integer totalChunks;
    
    @JsonProperty("received_chunks")
    private Integer receivedChunks;
    
    @JsonProperty("total_file_size")
    private Long totalFileSize;
    
    @JsonProperty("received_size")
    private Long receivedSize;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("progress")
    private Double progress;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("completed_at")
    private LocalDateTime completedAt;
    
    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    // Constructors
    public ChunkUploadSessionDto() {}
    
    public ChunkUploadSessionDto(String sessionId, String fileId, String filePath) {
        this.sessionId = sessionId;
        this.fileId = fileId;
        this.filePath = filePath;
    }
    
    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Double getProgress() {
        return progress;
    }
    
    public void setProgress(Double progress) {
        this.progress = progress;
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
    
    public boolean isComplete() {
        return "COMPLETED".equals(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equals(status) || "EXPIRED".equals(status);
    }
    
    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status);
    }
}
