package com.filesync.server.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.filesync.server.entity.ChunkUploadSessionEntity;
import com.filesync.server.entity.UserEntity;

/**
 * Repository for chunk upload session entities
 */
@Repository
public interface ChunkUploadSessionRepository extends JpaRepository<ChunkUploadSessionEntity, String> {
    
    /**
     * Find session by ID and user
     */
    Optional<ChunkUploadSessionEntity> findBySessionIdAndUser(String sessionId, UserEntity user);
    
    /**
     * Find all sessions for a user
     */
    List<ChunkUploadSessionEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    
    /**
     * Find sessions by status
     */
    List<ChunkUploadSessionEntity> findByStatus(ChunkUploadSessionEntity.UploadStatus status);
    
    /**
     * Find expired sessions
     */
    @Query("SELECT s FROM ChunkUploadSessionEntity s WHERE s.expiresAt < :currentTime")
    List<ChunkUploadSessionEntity> findExpiredSessions(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find sessions by file ID and user
     */
    List<ChunkUploadSessionEntity> findByFileIdAndUser(String fileId, UserEntity user);
    
    /**
     * Find active sessions for a user (not completed, failed, or expired)
     */
    @Query("SELECT s FROM ChunkUploadSessionEntity s WHERE s.user = :user " +
           "AND s.status = 'IN_PROGRESS' AND s.expiresAt > :currentTime")
    List<ChunkUploadSessionEntity> findActiveSessionsByUser(@Param("user") UserEntity user, 
                                                           @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Count active sessions for a user
     */
    @Query("SELECT COUNT(s) FROM ChunkUploadSessionEntity s WHERE s.user = :user " +
           "AND s.status = 'IN_PROGRESS' AND s.expiresAt > :currentTime")
    long countActiveSessionsByUser(@Param("user") UserEntity user, @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Delete expired sessions
     */
    @Modifying
    @Query("DELETE FROM ChunkUploadSessionEntity s WHERE s.expiresAt < :currentTime")
    int deleteExpiredSessions(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Delete completed sessions older than specified time
     */
    @Modifying
    @Query("DELETE FROM ChunkUploadSessionEntity s WHERE s.status = 'COMPLETED' " +
           "AND s.completedAt < :cutoffTime")
    int deleteOldCompletedSessions(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find sessions that need cleanup (failed or expired)
     */
    @Query("SELECT s FROM ChunkUploadSessionEntity s WHERE " +
           "(s.status = 'FAILED' OR s.status = 'EXPIRED' OR s.expiresAt < :currentTime) " +
           "AND s.createdAt < :cleanupTime")
    List<ChunkUploadSessionEntity> findSessionsForCleanup(@Param("currentTime") LocalDateTime currentTime,
                                                         @Param("cleanupTime") LocalDateTime cleanupTime);
}
