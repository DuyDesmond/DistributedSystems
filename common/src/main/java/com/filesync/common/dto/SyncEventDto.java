package com.filesync.common.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for sync events
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncEventDto {
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("file_id")
    private String fileId;
    
    @JsonProperty("event_type")
    private String eventType; // create, modify, delete
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    @JsonProperty("client_id")
    private String clientId;
    
    @JsonProperty("sync_status")
    private String syncStatus;
    
    @JsonProperty("file_path")
    private String filePath;
    
    @JsonProperty("checksum")
    private String checksum;
    
    @JsonProperty("file_size")
    private Long fileSize;
    
    // Constructors
    public SyncEventDto() {}
    
    public SyncEventDto(String userId, String fileId, String eventType, String clientId) {
        this.userId = userId;
        this.fileId = fileId;
        this.eventType = eventType;
        this.clientId = clientId;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
}
