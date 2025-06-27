package com.filesync.server.controller;

import com.filesync.common.dto.FileDto;
import com.filesync.server.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * File Operations Controller
 */
@RestController
@RequestMapping("/files")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FileController {
    
    @Autowired
    private FileService fileService;
    
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
}
