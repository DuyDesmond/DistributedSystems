package com.filesync.server.entity;

import com.filesync.common.model.VersionVector;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a file in the system
 */
@Entity
@Table(name = "files", indexes = {
    @Index(name = "idx_files_user_path", columnList = "user_id, file_path"),
    @Index(name = "idx_files_checksum", columnList = "checksum")
})
public class FileEntity {
    
    @Id
    @Column(name = "file_id")
    private String fileId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    private UserEntity user;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "checksum")
    private String checksum;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_version_vector", columnDefinition = "jsonb")
    private VersionVector currentVersionVector;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;
    
    @Column(name = "sync_status")
    private String syncStatus;
    
    @Column(name = "conflict_status")
    private String conflictStatus;
    
    @Column(name = "storage_path")
    private String storagePath;
    
    // Relationships
    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FileVersionEntity> versions = new ArrayList<>();
    
    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SyncEventEntity> syncEvents = new ArrayList<>();
    
    // Constructors
    public FileEntity() {}
    
    public FileEntity(String fileId, UserEntity user, String filePath, String fileName) {
        this.fileId = fileId;
        this.user = user;
        this.filePath = filePath;
        this.fileName = fileName;
        this.currentVersionVector = new VersionVector();
        this.syncStatus = "PENDING";
        this.conflictStatus = "NONE";
    }
    
    // Getters and setters
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public UserEntity getUser() {
        return user;
    }
    
    public void setUser(UserEntity user) {
        this.user = user;
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
    
    public VersionVector getCurrentVersionVector() {
        return currentVersionVector;
    }
    
    public void setCurrentVersionVector(VersionVector currentVersionVector) {
        this.currentVersionVector = currentVersionVector;
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
    
    public String getStoragePath() {
        return storagePath;
    }
    
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
    
    public List<FileVersionEntity> getVersions() {
        return versions;
    }
    
    public void setVersions(List<FileVersionEntity> versions) {
        this.versions = versions;
    }
    
    public List<SyncEventEntity> getSyncEvents() {
        return syncEvents;
    }
    
    public void setSyncEvents(List<SyncEventEntity> syncEvents) {
        this.syncEvents = syncEvents;
    }
}
