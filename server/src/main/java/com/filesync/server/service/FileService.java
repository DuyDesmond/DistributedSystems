package com.filesync.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.filesync.common.dto.FileDto;
import com.filesync.server.entity.FileEntity;
import com.filesync.server.entity.UserEntity;
import com.filesync.server.repository.FileRepository;
import com.filesync.server.repository.UserRepository;
import com.filesync.server.util.StoragePathUtil;

import jakarta.servlet.http.HttpServletResponse;

/**
 * File Service for handling file operations
 */
@Service
public class FileService {
    
    @Autowired
    private FileRepository fileRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ChunkService chunkService;
    
    @Value("${filesync.storage.base-path:./storage}")
    private String storageBasePath;
    
    @Value("${filesync.storage.max-file-size:104857600}")
    private long maxFileSize;
    
    public List<FileDto> getUserFiles(String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return fileRepository.findByUserOrderByModifiedAtDesc(user)
            .stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }
    
    public FileDto uploadFile(MultipartFile file, String path, String username) throws IOException {
        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("File size exceeds maximum allowed size");
        }
        
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Check if file already exists
        FileEntity existingFile = fileRepository.findByUserAndFilePath(user, path).orElse(null);
        
        String fileId = existingFile != null ? existingFile.getFileId() : UUID.randomUUID().toString();
        String checksum = calculateChecksum(file.getBytes());
        
        // Create storage path
        String storagePath = createStoragePath(user.getUserId(), fileId);
        
        // Save file to storage
        saveFileToStorage(file.getBytes(), storagePath);
        
        FileEntity fileEntity;
        if (existingFile != null) {
            // Update existing file - FIXED: Don't increment version vector for simple updates
            // Let the SyncService handle version vector management during sync operations
            existingFile.setFileSize(file.getSize());
            existingFile.setChecksum(checksum);
            existingFile.setModifiedAt(LocalDateTime.now());
            existingFile.setStoragePath(storagePath);
            // Clear any conflict status since we're successfully uploading
            existingFile.setConflictStatus(null);
            fileEntity = fileRepository.save(existingFile);
        } else {
            // Create new file
            fileEntity = new FileEntity(fileId, user, path, file.getOriginalFilename());
            fileEntity.setFileSize(file.getSize());
            fileEntity.setChecksum(checksum);
            fileEntity.getCurrentVersionVector().increment(user.getUserId());
            fileEntity.setStoragePath(storagePath);
            fileEntity = fileRepository.save(fileEntity);
        }
        
        return convertToDto(fileEntity);
    }
    
    public void downloadFile(String fileId, String username, HttpServletResponse response) throws IOException {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        FileEntity fileEntity = fileRepository.findByFileIdAndUser(fileId, user)
            .orElseThrow(() -> new RuntimeException("File not found"));
        
        Path filePath = Paths.get(fileEntity.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found on storage");
        }
        
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileEntity.getFileName() + "\"");
        response.setContentLengthLong(fileEntity.getFileSize());
        
        try (InputStream inputStream = Files.newInputStream(filePath);
             OutputStream outputStream = response.getOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }
    
    public FileDto updateFile(String fileId, MultipartFile file, String username) throws IOException {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        FileEntity fileEntity = fileRepository.findByFileIdAndUser(fileId, user)
            .orElseThrow(() -> new RuntimeException("File not found"));
        
        return uploadFile(file, fileEntity.getFilePath(), username);
    }
    
    public void deleteFile(String fileId, String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        FileEntity fileEntity = fileRepository.findByFileIdAndUser(fileId, user)
            .orElseThrow(() -> new RuntimeException("File not found"));
        
        // Delete file from storage
        try {
            Path filePath = Paths.get(fileEntity.getStoragePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log error but continue with database deletion
        }
        
        fileRepository.delete(fileEntity);
    }
    
    public List<FileDto> getFileVersions(String fileId, String username) {
        // For now, return the current version only
        // In a full implementation, this would return all versions from a versions table
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        FileEntity fileEntity = fileRepository.findByFileIdAndUser(fileId, user)
            .orElseThrow(() -> new RuntimeException("File not found"));
        
        return List.of(convertToDto(fileEntity));
    }
    
    private FileDto convertToDto(FileEntity entity) {
        FileDto dto = new FileDto();
        dto.setFileId(entity.getFileId());
        dto.setUserId(entity.getUser().getUserId());
        dto.setFilePath(entity.getFilePath());
        dto.setFileName(entity.getFileName());
        dto.setFileSize(entity.getFileSize());
        dto.setChecksum(entity.getChecksum());
        dto.setVersionVector(entity.getCurrentVersionVector());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setModifiedAt(entity.getModifiedAt());
        dto.setSyncStatus(entity.getSyncStatus());
        dto.setConflictStatus(entity.getConflictStatus());
        return dto;
    }
    
    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private String createStoragePath(String userId, String fileId) {
        return StoragePathUtil.createStoragePath(storageBasePath, userId, fileId);
    }
    
    private void saveFileToStorage(byte[] data, String storagePath) throws IOException {
        Path path = Paths.get(storagePath);
        Files.write(path, data);
    }
}
