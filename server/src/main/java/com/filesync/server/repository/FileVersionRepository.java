package com.filesync.server.repository;

import com.filesync.server.entity.FileEntity;
import com.filesync.server.entity.FileVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FileVersion entities
 */
@Repository
public interface FileVersionRepository extends JpaRepository<FileVersionEntity, String> {
    
    /**
     * Find all versions for a specific file, ordered by version number descending
     */
    List<FileVersionEntity> findByFileOrderByVersionNumberDesc(FileEntity file);
    
    /**
     * Find the current version for a specific file
     */
    Optional<FileVersionEntity> findByFileAndIsCurrentVersion(FileEntity file, boolean isCurrentVersion);
    
    /**
     * Find a specific version by file and version number
     */
    Optional<FileVersionEntity> findByFileAndVersionNumber(FileEntity file, int versionNumber);
    
    /**
     * Find versions by checksum (for deduplication)
     */
    List<FileVersionEntity> findByChecksum(String checksum);
    
    /**
     * Get the highest version number for a file
     */
    @Query("SELECT MAX(fv.versionNumber) FROM FileVersionEntity fv WHERE fv.file = :file")
    Optional<Integer> findMaxVersionNumberByFile(@Param("file") FileEntity file);
    
    /**
     * Mark all versions as non-current for a file
     */
    @Modifying
    @Query("UPDATE FileVersionEntity fv SET fv.isCurrentVersion = false WHERE fv.file = :file")
    void markAllVersionsAsNonCurrent(@Param("file") FileEntity file);
    
    /**
     * Find versions created by a specific client
     */
    List<FileVersionEntity> findByCreatedByClient(String clientId);
    
    /**
     * Count total versions for a file
     */
    long countByFile(FileEntity file);
    
    /**
     * Find versions within a size range
     */
    List<FileVersionEntity> findByFileSizeBetween(long minSize, long maxSize);
}
