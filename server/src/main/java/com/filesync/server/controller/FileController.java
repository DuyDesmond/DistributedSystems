package com.filesync.server.controller;

import java.io.IOException;
import java.util.List;

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
    public ResponseEntity<ChunkUploadSessionEntity> initiateChunkedUpload(
            @RequestParam("fileId") String fileId,
            @RequestParam("filePath") String filePath,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("totalFileSize") Long totalFileSize,
            Authentication authentication) {
        try {
            ChunkUploadSessionEntity session = chunkService.initiateChunkedUpload(
                authentication.getName(), fileId, filePath, totalChunks, totalFileSize);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Upload a single chunk
     */
    @PostMapping("/upload/chunk")
    public ResponseEntity<ChunkUploadSessionEntity> uploadChunk(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("chunkData") MultipartFile chunkData,
            Authentication authentication) {
        try {
            ChunkUploadSessionEntity session = chunkService.uploadChunk(
                authentication.getName(), sessionId, chunkIndex, chunkData);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get upload session status
     */
    @GetMapping("/upload/status/{sessionId}")
    public ResponseEntity<ChunkUploadSessionEntity> getUploadStatus(
            @PathVariable String sessionId,
            Authentication authentication) {
        try {
            ChunkUploadSessionEntity session = chunkService.getUploadStatus(
                authentication.getName(), sessionId);
            return ResponseEntity.ok(session);
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
    public ResponseEntity<List<ChunkUploadSessionEntity>> getActiveUploadSessions(
            Authentication authentication) {
        try {
            List<ChunkUploadSessionEntity> sessions = chunkService.getActiveUploadSessions(
                authentication.getName());
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
