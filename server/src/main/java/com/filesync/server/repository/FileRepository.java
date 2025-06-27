package com.filesync.server.repository;

import com.filesync.server.entity.FileEntity;
import com.filesync.server.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for FileEntity
 */
@Repository
public interface FileRepository extends JpaRepository<FileEntity, String> {
    
    List<FileEntity> findByUserOrderByModifiedAtDesc(UserEntity user);
    
    Optional<FileEntity> findByUserAndFilePath(UserEntity user, String filePath);
    
    Optional<FileEntity> findByFileIdAndUser(String fileId, UserEntity user);
    
    List<FileEntity> findByUserAndSyncStatus(UserEntity user, String syncStatus);
    
    List<FileEntity> findByChecksum(String checksum);
}
