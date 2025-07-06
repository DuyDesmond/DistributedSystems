package com.filesync.common.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.filesync.common.model.VersionVector;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for file metadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileDto {
    
    @JsonProperty("file_id")
    private String fileId;
    
    @JsonProperty("user_id")
    @NotBlank
    private String userId;
    
    @JsonProperty("file_path")
    @NotBlank
    private String filePath;
    
    @JsonProperty("file_name")
    @NotBlank
    private String fileName;
    
    @JsonProperty("file_size")
    private Long fileSize;
    
    @JsonProperty("checksum")
    private String checksum;
    
    @JsonProperty("version_vector")
    private VersionVector versionVector;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("modified_at")
    private LocalDateTime modifiedAt;
    
    @JsonProperty("sync_status")
    private String syncStatus;
    
    @JsonProperty("conflict_status")
    private String conflictStatus;
    
    @JsonProperty("content")
    private byte[] content; // For small files or chunks
    
    @JsonProperty("chunk_index")
    private Integer chunkIndex; // For chunked uploads
    
    @JsonProperty("total_chunks")
    private Integer totalChunks;
    
    // Constructors
    public FileDto() {}
    
    public FileDto(String userId, String filePath, String fileName) {
        this.userId = userId;
        this.filePath = filePath;
        this.fileName = fileName;
        this.versionVector = new VersionVector();
    }
    
    // Getters and setters
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    public VersionVector getVersionVector() {
        return versionVector;
    }
    
    public void setVersionVector(VersionVector versionVector) {
        this.versionVector = versionVector;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getModifiedAt() {
        return modifiedAt;
    }
    
    public void setModifiedAt(LocalDateTime modifiedAt) {
        this.modifiedAt = modifiedAt;
    }
    
    public String getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
    
    public String getConflictStatus() {
        return conflictStatus;
    }
    
    public void setConflictStatus(String conflictStatus) {
        this.conflictStatus = conflictStatus;
    }
    
    public byte[] getContent() {
        return content;
    }
    
    public void setContent(byte[] content) {
        this.content = content;
    }
    
    public Integer getChunkIndex() {
        return chunkIndex;
    }
    
    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
    
    public Integer getTotalChunks() {
        return totalChunks;
    }
    
    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }
}
