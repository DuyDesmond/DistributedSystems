package com.filesync.common.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object for file chunks in chunked upload/download
 */
public class FileChunkDto {
    
    @JsonProperty("chunk_id")
    private String chunkId;
    
    @JsonProperty("file_id")
    @NotBlank
    private String fileId;
    
    @JsonProperty("upload_session_id")
    private String uploadSessionId;
    
    @JsonProperty("chunk_index")
    @NotNull
    private Integer chunkIndex;
    
    @JsonProperty("total_chunks")
    @NotNull
    private Integer totalChunks;
    
    @JsonProperty("chunk_size")
    private Long chunkSize;
    
    @JsonProperty("chunk_data")
    private byte[] chunkData;
    
    @JsonProperty("chunk_checksum")
    private String chunkChecksum;
    
    @JsonProperty("is_last_chunk")
    private Boolean isLastChunk;
    
    @JsonProperty("file_path")
    private String filePath;
    
    @JsonProperty("total_file_size")
    private Long totalFileSize;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    // Constructors
    public FileChunkDto() {
        this.createdAt = LocalDateTime.now();
    }
    
    public FileChunkDto(String fileId, String uploadSessionId, Integer chunkIndex, Integer totalChunks) {
        this();
        this.fileId = fileId;
        this.uploadSessionId = uploadSessionId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
    }
    
    // Getters and setters
    public String getChunkId() {
        return chunkId;
    }
    
    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getUploadSessionId() {
        return uploadSessionId;
    }
    
    public void setUploadSessionId(String uploadSessionId) {
        this.uploadSessionId = uploadSessionId;
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
    
    public Long getChunkSize() {
        return chunkSize;
    }
    
    public void setChunkSize(Long chunkSize) {
        this.chunkSize = chunkSize;
    }
    
    public byte[] getChunkData() {
        return chunkData;
    }
    
    public void setChunkData(byte[] chunkData) {
        this.chunkData = chunkData;
        this.chunkSize = chunkData != null ? (long) chunkData.length : null;
    }
    
    public String getChunkChecksum() {
        return chunkChecksum;
    }
    
    public void setChunkChecksum(String chunkChecksum) {
        this.chunkChecksum = chunkChecksum;
    }
    
    public Boolean getIsLastChunk() {
        return isLastChunk;
    }
    
    public void setIsLastChunk(Boolean isLastChunk) {
        this.isLastChunk = isLastChunk;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public Long getTotalFileSize() {
        return totalFileSize;
    }
    
    public void setTotalFileSize(Long totalFileSize) {
        this.totalFileSize = totalFileSize;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "FileChunkDto{" +
                "chunkId='" + chunkId + '\'' +
                ", fileId='" + fileId + '\'' +
                ", uploadSessionId='" + uploadSessionId + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", totalChunks=" + totalChunks +
                ", chunkSize=" + chunkSize +
                ", isLastChunk=" + isLastChunk +
                '}';
    }
}
