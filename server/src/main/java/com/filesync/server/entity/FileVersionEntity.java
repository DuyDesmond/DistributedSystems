package com.filesync.server.entity;

import java.time.LocalDateTime;

import com.filesync.common.model.VersionVector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity representing file versions with version vectors for conflict detection
 */
@Entity
@Table(name = "file_versions", indexes = {
    @Index(name = "idx_file_versions_file_id", columnList = "file_id"),
    @Index(name = "idx_file_versions_version", columnList = "version_number"),
    @Index(name = "idx_file_versions_current", columnList = "is_current_version")
})
public class FileVersionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "version_id")
    private String versionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;
    
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
    
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;
    
    @Column(name = "storage_path", nullable = false)
    private String storagePath;
    
    @Column(name = "file_size", nullable = false)
    private long fileSize;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "is_current_version", nullable = false)
    private boolean isCurrentVersion;
    
    @Column(name = "version_vector", columnDefinition = "TEXT")
    private String versionVectorJson;
    
    @Column(name = "created_by_client")
    private String createdByClient;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    // Constructors
    public FileVersionEntity() {}
    
    public FileVersionEntity(FileEntity file, int versionNumber, String checksum, 
                           String storagePath, long fileSize, boolean isCurrentVersion) {
        this.file = file;
        this.versionNumber = versionNumber;
        this.checksum = checksum;
        this.storagePath = storagePath;
        this.fileSize = fileSize;
        this.isCurrentVersion = isCurrentVersion;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getVersionId() {
        return versionId;
    }
    
    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }
    
    public FileEntity getFile() {
        return file;
    }
    
    public void setFile(FileEntity file) {
        this.file = file;
    }
    
    public int getVersionNumber() {
        return versionNumber;
    }
    
    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    public String getStoragePath() {
        return storagePath;
    }
    
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public boolean isCurrentVersion() {
        return isCurrentVersion;
    }
    
    public void setCurrentVersion(boolean currentVersion) {
        isCurrentVersion = currentVersion;
    }
    
    public String getVersionVectorJson() {
        return versionVectorJson;
    }
    
    public void setVersionVectorJson(String versionVectorJson) {
        this.versionVectorJson = versionVectorJson;
    }
    
    public String getCreatedByClient() {
        return createdByClient;
    }
    
    public void setCreatedByClient(String createdByClient) {
        this.createdByClient = createdByClient;
    }
    
    // Helper methods for version vector
    public VersionVector getVersionVector() {
        if (versionVectorJson == null || versionVectorJson.trim().isEmpty()) {
            return new VersionVector();
        }
        return VersionVector.fromJson(versionVectorJson);
    }
    
    public void setVersionVector(VersionVector versionVector) {
        this.versionVectorJson = versionVector.toJson();
    }
}
