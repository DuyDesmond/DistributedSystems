package com.filesync.server.controller;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filesync.server.entity.FileEntity;
import com.filesync.server.entity.UserEntity;
import com.filesync.server.repository.FileRepository;
import com.filesync.server.repository.UserRepository;
import com.filesync.server.service.FileService;

/**
 * Controller for handling chunked file downloads with HTTP Range support
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ChunkedDownloadController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChunkedDownloadController.class);
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private FileRepository fileRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Download file with support for HTTP Range requests (chunked downloads)
     */
    @GetMapping("/{fileId}/download-chunked")
    public ResponseEntity<Resource> downloadFileChunked(
            @PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            Authentication authentication) {
        
        try {
            String username = authentication.getName();
            UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            FileEntity fileEntity = fileRepository.findByFileIdAndUser(fileId, user)
                .orElseThrow(() -> new RuntimeException("File not found"));
            
            Path filePath = Paths.get(fileEntity.getStoragePath());
            if (!Files.exists(filePath)) {
                logger.error("File not found on storage: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            long fileSize = Files.size(filePath);
            
            // Handle range requests for chunked downloads
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(filePath, fileEntity.getFileName(), fileSize, rangeHeader);
            } else {
                // Full file download
                return handleFullFileDownload(filePath, fileEntity.getFileName(), fileSize);
            }
            
        } catch (Exception e) {
            logger.error("Error during chunked download for file: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Handle HTTP Range request for partial content download
     */
    private ResponseEntity<Resource> handleRangeRequest(Path filePath, String fileName, 
                                                       long fileSize, String rangeHeader) throws IOException {
        
        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader);
        if (!matcher.matches()) {
            logger.warn("Invalid range header: {}", rangeHeader);
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                .build();
        }
        
        long rangeStart = Long.parseLong(matcher.group(1));
        String endGroup = matcher.group(2);
        long rangeEnd = endGroup.isEmpty() ? fileSize - 1 : Long.parseLong(endGroup);
        
        // Validate range
        if (rangeStart < 0 || rangeEnd >= fileSize || rangeStart > rangeEnd) {
            logger.warn("Invalid range: {}-{} for file size: {}", rangeStart, rangeEnd, fileSize);
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                .build();
        }
        
        long contentLength = rangeEnd - rangeStart + 1;
        
        logger.debug("Range request for file {}: bytes {}-{}/{} (length: {})", 
            fileName, rangeStart, rangeEnd, fileSize, contentLength);
        
        // Create range resource
        RangeResource rangeResource = new RangeResource(filePath, rangeStart, contentLength);
        
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
            .header(HttpHeaders.CONTENT_RANGE, "bytes " + rangeStart + "-" + rangeEnd + "/" + fileSize)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body(rangeResource);
    }
    
    /**
     * Handle full file download (non-chunked)
     */
    private ResponseEntity<Resource> handleFullFileDownload(Path filePath, String fileName, long fileSize) {
        
        logger.debug("Full file download for: {} (size: {})", fileName, fileSize);
        
        FileSystemResource resource = new FileSystemResource(filePath);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body(resource);
    }
    
    /**
     * Get file metadata for chunked download planning
     */
    @GetMapping("/{fileId}/metadata")
    public ResponseEntity<FileMetadata> getFileMetadata(
            @PathVariable String fileId,
            Authentication authentication) {
        
        try {
            String username = authentication.getName();
            UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            FileEntity fileEntity = fileRepository.findByFileIdAndUser(fileId, user)
                .orElseThrow(() -> new RuntimeException("File not found"));
            
            Path filePath = Paths.get(fileEntity.getStoragePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            
            long fileSize = Files.size(filePath);
            
            FileMetadata metadata = new FileMetadata(
                fileEntity.getFileId(),
                fileEntity.getFileName(),
                fileSize,
                fileEntity.getChecksum(),
                true // supportsRangeRequests
            );
            
            return ResponseEntity.ok(metadata);
            
        } catch (Exception e) {
            logger.error("Error getting file metadata for: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Custom Resource implementation for range requests
     */
    private static class RangeResource extends FileSystemResource {
        private final long offset;
        private final long length;
        
        public RangeResource(Path filePath, long offset, long length) {
            super(filePath);
            this.offset = offset;
            this.length = length;
        }
        
        @Override
        public long contentLength() throws IOException {
            return length;
        }
        
        @Override
        public java.io.InputStream getInputStream() throws IOException {
            RandomAccessFile randomAccessFile = new RandomAccessFile(getFile(), "r");
            randomAccessFile.seek(offset);
            
            return new java.io.InputStream() {
                private long bytesRead = 0;
                
                @Override
                public int read() throws IOException {
                    if (bytesRead >= length) {
                        return -1;
                    }
                    bytesRead++;
                    return randomAccessFile.read();
                }
                
                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (bytesRead >= length) {
                        return -1;
                    }
                    
                    int maxRead = (int) Math.min(len, length - bytesRead);
                    int actualRead = randomAccessFile.read(b, off, maxRead);
                    
                    if (actualRead > 0) {
                        bytesRead += actualRead;
                    }
                    
                    return actualRead;
                }
                
                @Override
                public void close() throws IOException {
                    randomAccessFile.close();
                }
            };
        }
    }
    
    /**
     * File metadata DTO for download planning
     */
    public static class FileMetadata {
        private String fileId;
        private String fileName;
        private long fileSize;
        private String checksum;
        private boolean supportsRangeRequests;
        
        public FileMetadata(String fileId, String fileName, long fileSize, String checksum, boolean supportsRangeRequests) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.checksum = checksum;
            this.supportsRangeRequests = supportsRangeRequests;
        }
        
        // Getters
        public String getFileId() { return fileId; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public String getChecksum() { return checksum; }
        public boolean isSupportsRangeRequests() { return supportsRangeRequests; }
        
        // Setters
        public void setFileId(String fileId) { this.fileId = fileId; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        public void setSupportsRangeRequests(boolean supportsRangeRequests) { this.supportsRangeRequests = supportsRangeRequests; }
    }
}
