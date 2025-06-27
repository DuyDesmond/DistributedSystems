package com.filesync.server.repository;

import com.filesync.server.entity.FileEntity;
import com.filesync.server.entity.SyncEventEntity;
import com.filesync.server.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for SyncEvent entities
 */
@Repository
public interface SyncEventRepository extends JpaRepository<SyncEventEntity, String> {
    
    /**
     * Find sync events for a user, ordered by timestamp descending
     */
    Page<SyncEventEntity> findByUserOrderByTimestampDesc(UserEntity user, Pageable pageable);
    
    /**
     * Find sync events for a specific file
     */
    List<SyncEventEntity> findByFileOrderByTimestampDesc(FileEntity file);
    
    /**
     * Find sync events by client ID
     */
    List<SyncEventEntity> findByClientIdOrderByTimestampDesc(String clientId);
    
    /**
     * Find sync events by status
     */
    List<SyncEventEntity> findBySyncStatusOrderByTimestampDesc(SyncEventEntity.SyncStatus syncStatus);
    
    /**
     * Find sync events by user and status
     */
    List<SyncEventEntity> findByUserAndSyncStatusOrderByTimestampDesc(UserEntity user, SyncEventEntity.SyncStatus syncStatus);
    
    /**
     * Find sync events within a time range
     */
    List<SyncEventEntity> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Find sync events by user and time range
     */
    List<SyncEventEntity> findByUserAndTimestampBetweenOrderByTimestampDesc(
        UserEntity user, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Find failed sync events
     */
    List<SyncEventEntity> findBySyncStatusInOrderByTimestampDesc(List<SyncEventEntity.SyncStatus> statuses);
    
    /**
     * Count sync events by user
     */
    long countByUser(UserEntity user);
    
    /**
     * Count sync events by user and status
     */
    long countByUserAndSyncStatus(UserEntity user, SyncEventEntity.SyncStatus syncStatus);
    
    /**
     * Find recent sync events for a user (last N hours)
     */
    @Query("SELECT se FROM SyncEventEntity se WHERE se.user = :user AND se.timestamp >= :since ORDER BY se.timestamp DESC")
    List<SyncEventEntity> findRecentByUser(@Param("user") UserEntity user, @Param("since") LocalDateTime since);
    
    /**
     * Find sync events for a user since last sync
     */
    @Query("SELECT se FROM SyncEventEntity se WHERE se.user = :user AND se.timestamp > :lastSync ORDER BY se.timestamp ASC")
    List<SyncEventEntity> findByUserSinceLastSync(@Param("user") UserEntity user, @Param("lastSync") LocalDateTime lastSync);
    
    /**
     * Clean up old sync events (older than specified date)
     */
    void deleteByTimestampBefore(LocalDateTime cutoffTime);
}
