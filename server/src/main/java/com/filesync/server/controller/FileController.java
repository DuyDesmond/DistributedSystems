package com.filesync.server.controller;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.filesync.common.dto.ChunkUploadSessionDto;
import com.filesync.common.dto.FileDto;
import com.filesync.server.entity.ChunkUploadSessionEntity;
import com.filesync.server.service.ChunkService;
import com.filesync.server.service.FileService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * File Operations Controller
 */
@RestController
@RequestMapping("/files")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FileController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private ChunkService chunkService;
    
    @GetMapping("/")
    public ResponseEntity<List<FileDto>> getUserFiles(Authentication authentication) {
        try {
            List<FileDto> files = fileService.getUserFiles(authentication.getName());
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/upload")
    public ResponseEntity<FileDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path,
            Authentication authentication) {
        try {
            FileDto fileDto = fileService.uploadFile(file, path, authentication.getName());
            return ResponseEntity.ok(fileDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/{fileId}/download")
    public void downloadFile(@PathVariable String fileId, 
                           Authentication authentication,
                           HttpServletResponse response) throws IOException {
        try {
            fileService.downloadFile(fileId, authentication.getName(), response);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
        }
    }
    
    @PutMapping("/{fileId}")
    public ResponseEntity<FileDto> updateFile(
            @PathVariable String fileId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            FileDto fileDto = fileService.updateFile(fileId, file, authentication.getName());
            return ResponseEntity.ok(fileDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable String fileId,
                                       Authentication authentication) {
        try {
            fileService.deleteFile(fileId, authentication.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/{fileId}/versions")
    public ResponseEntity<List<FileDto>> getFileVersions(@PathVariable String fileId,
                                                         Authentication authentication) {
        try {
            List<FileDto> versions = fileService.getFileVersions(fileId, authentication.getName());
            return ResponseEntity.ok(versions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Initiate chunked upload session
     */
    @PostMapping("/upload/initiate-chunked")
    public ResponseEntity<?> initiateChunkedUpload(
            @RequestParam("fileId") String fileId,
            @RequestParam("filePath") String filePath,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("totalFileSize") Long totalFileSize,
            Authentication authentication) {
        try {
            ChunkUploadSessionEntity session = chunkService.initiateChunkedUpload(
                authentication.getName(), fileId, filePath, totalChunks, totalFileSize);
            
            // Convert entity to DTO for response
            ChunkUploadSessionDto sessionDto = new ChunkUploadSessionDto();
            sessionDto.setSessionId(session.getSessionId());
            sessionDto.setFileId(session.getFileId());
            sessionDto.setFilePath(session.getFilePath());
            sessionDto.setTotalChunks(session.getTotalChunks());
            sessionDto.setReceivedChunks(session.getReceivedChunks());
            sessionDto.setTotalFileSize(session.getTotalFileSize());
            sessionDto.setReceivedSize(session.getReceivedSize());
            sessionDto.setStatus(session.getStatus().toString());
            sessionDto.setProgress(session.getProgress());
            sessionDto.setCreatedAt(session.getCreatedAt());
            sessionDto.setExpiresAt(session.getExpiresAt());
            
            return ResponseEntity.ok(sessionDto);
        } catch (Exception e) {
            logger.error("Failed to initiate chunked upload for user {} and file {}: {}", 
                authentication.getName(), filePath, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Upload a single chunk
     */
    @PostMapping("/upload/chunk")
    public ResponseEntity<ChunkUploadSessionDto> uploadChunk(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("chunkData") MultipartFile chunkData,
            Authentication authentication) {
        try {
            ChunkUploadSessionEntity session = chunkService.uploadChunk(
                authentication.getName(), sessionId, chunkIndex, chunkData);
                
            // Convert entity to DTO for response
            ChunkUploadSessionDto sessionDto = new ChunkUploadSessionDto();
            sessionDto.setSessionId(session.getSessionId());
            sessionDto.setFileId(session.getFileId());
            sessionDto.setFilePath(session.getFilePath());
            sessionDto.setTotalChunks(session.getTotalChunks());
            sessionDto.setReceivedChunks(session.getReceivedChunks());
            sessionDto.setTotalFileSize(session.getTotalFileSize());
            sessionDto.setReceivedSize(session.getReceivedSize());
            sessionDto.setStatus(session.getStatus().toString());
            sessionDto.setProgress(session.getProgress());
            sessionDto.setCreatedAt(session.getCreatedAt());
            sessionDto.setCompletedAt(session.getCompletedAt());
            sessionDto.setExpiresAt(session.getExpiresAt());
            sessionDto.setErrorMessage(session.getErrorMessage());
            
            return ResponseEntity.ok(sessionDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get upload session status
     */
    @GetMapping("/upload/status/{sessionId}")
    public ResponseEntity<ChunkUploadSessionDto> getUploadStatus(
            @PathVariable String sessionId,
            Authentication authentication) {
        try {
            ChunkUploadSessionEntity session = chunkService.getUploadStatus(
                authentication.getName(), sessionId);
                
            // Convert entity to DTO for response
            ChunkUploadSessionDto sessionDto = new ChunkUploadSessionDto();
            sessionDto.setSessionId(session.getSessionId());
            sessionDto.setFileId(session.getFileId());
            sessionDto.setFilePath(session.getFilePath());
            sessionDto.setTotalChunks(session.getTotalChunks());
            sessionDto.setReceivedChunks(session.getReceivedChunks());
            sessionDto.setTotalFileSize(session.getTotalFileSize());
            sessionDto.setReceivedSize(session.getReceivedSize());
            sessionDto.setStatus(session.getStatus().toString());
            sessionDto.setProgress(session.getProgress());
            sessionDto.setCreatedAt(session.getCreatedAt());
            sessionDto.setCompletedAt(session.getCompletedAt());
            sessionDto.setExpiresAt(session.getExpiresAt());
            sessionDto.setErrorMessage(session.getErrorMessage());
            
            return ResponseEntity.ok(sessionDto);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Cancel upload session
     */
    @DeleteMapping("/upload/cancel/{sessionId}")
    public ResponseEntity<?> cancelUploadSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        try {
            chunkService.cancelUploadSession(authentication.getName(), sessionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get active upload sessions
     */
    @GetMapping("/upload/sessions")
    public ResponseEntity<List<ChunkUploadSessionDto>> getActiveUploadSessions(
            Authentication authentication) {
        try {
            List<ChunkUploadSessionEntity> sessions = chunkService.getActiveUploadSessions(
                authentication.getName());
                
            // Convert entities to DTOs
            List<ChunkUploadSessionDto> sessionDtos = sessions.stream().map(session -> {
                ChunkUploadSessionDto dto = new ChunkUploadSessionDto();
                dto.setSessionId(session.getSessionId());
                dto.setFileId(session.getFileId());
                dto.setFilePath(session.getFilePath());
                dto.setTotalChunks(session.getTotalChunks());
                dto.setReceivedChunks(session.getReceivedChunks());
                dto.setTotalFileSize(session.getTotalFileSize());
                dto.setReceivedSize(session.getReceivedSize());
                dto.setStatus(session.getStatus().toString());
                dto.setProgress(session.getProgress());
                dto.setCreatedAt(session.getCreatedAt());
                dto.setCompletedAt(session.getCompletedAt());
                dto.setExpiresAt(session.getExpiresAt());
                dto.setErrorMessage(session.getErrorMessage());
                return dto;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(sessionDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
