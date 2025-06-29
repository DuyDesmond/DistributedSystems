package com.filesync.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.filesync.common.dto.FileChunkDto;

/**
 * Utility class for file chunking operations
 */
public class ChunkingUtil {
    
    // Default chunk size: 5MB
    public static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
    
    // Minimum file size for chunking: 10MB
    public static final long MIN_CHUNKING_SIZE = 10 * 1024 * 1024;
    
    // Maximum chunk size: 50MB (for very large files)
    public static final long MAX_CHUNK_SIZE = 50 * 1024 * 1024;
    
    // Minimum chunk size: 1MB
    public static final long MIN_CHUNK_SIZE = 1024 * 1024;
    
    /**
     * Determines if a file should be chunked based on its size
     */
    public static boolean shouldChunkFile(long fileSize) {
        return fileSize >= MIN_CHUNKING_SIZE;
    }
    
    /**
     * Calculates optimal chunk size based on file size
     */
    public static long calculateOptimalChunkSize(long fileSize) {
        if (fileSize < MIN_CHUNKING_SIZE) {
            return fileSize; // Don't chunk small files
        }
        
        // For very large files (>500MB), use larger chunks
        if (fileSize > 500 * 1024 * 1024) {
            return Math.min(MAX_CHUNK_SIZE, fileSize / 100); // ~100 chunks for very large files
        }
        
        // For medium files (10MB-500MB), use default chunk size
        return Math.max(MIN_CHUNK_SIZE, Math.min(DEFAULT_CHUNK_SIZE, fileSize / 20)); // ~20 chunks
    }
    
    /**
     * Chunks a file into multiple FileChunkDto objects
     */
    public static List<FileChunkDto> chunkFile(Path filePath, String fileId) throws IOException {
        return chunkFile(filePath, fileId, null);
    }
    
    /**
     * Chunks a file into multiple FileChunkDto objects with custom upload session ID
     */
    public static List<FileChunkDto> chunkFile(Path filePath, String fileId, String uploadSessionId) throws IOException {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IOException("File does not exist or is not a regular file: " + filePath);
        }
        
        long fileSize = Files.size(filePath);
        if (!shouldChunkFile(fileSize)) {
            throw new IllegalArgumentException("File size (" + fileSize + ") does not require chunking");
        }
        
        long chunkSize = calculateOptimalChunkSize(fileSize);
        byte[] fileBytes = Files.readAllBytes(filePath);
        int totalChunks = (int) Math.ceil((double) fileBytes.length / chunkSize);
        
        if (uploadSessionId == null) {
            uploadSessionId = UUID.randomUUID().toString();
        }
        
        List<FileChunkDto> chunks = new ArrayList<>();
        
        for (int i = 0; i < totalChunks; i++) {
            int start = (int) (i * chunkSize);
            int end = Math.min(start + (int) chunkSize, fileBytes.length);
            byte[] chunkData = Arrays.copyOfRange(fileBytes, start, end);
            
            FileChunkDto chunk = new FileChunkDto(fileId, uploadSessionId, i, totalChunks);
            chunk.setChunkId(UUID.randomUUID().toString());
            chunk.setChunkData(chunkData);
            chunk.setChunkChecksum(calculateChecksum(chunkData));
            chunk.setIsLastChunk(i == totalChunks - 1);
            chunk.setFilePath(filePath.toString());
            chunk.setTotalFileSize(fileSize);
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Chunks byte array data into multiple FileChunkDto objects
     */
    public static List<FileChunkDto> chunkData(byte[] data, String fileId, String uploadSessionId) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        long fileSize = data.length;
        if (!shouldChunkFile(fileSize)) {
            throw new IllegalArgumentException("Data size (" + fileSize + ") does not require chunking");
        }
        
        long chunkSize = calculateOptimalChunkSize(fileSize);
        int totalChunks = (int) Math.ceil((double) data.length / chunkSize);
        
        if (uploadSessionId == null) {
            uploadSessionId = UUID.randomUUID().toString();
        }
        
        List<FileChunkDto> chunks = new ArrayList<>();
        
        for (int i = 0; i < totalChunks; i++) {
            int start = (int) (i * chunkSize);
            int end = Math.min(start + (int) chunkSize, data.length);
            byte[] chunkData = Arrays.copyOfRange(data, start, end);
            
            FileChunkDto chunk = new FileChunkDto(fileId, uploadSessionId, i, totalChunks);
            chunk.setChunkId(UUID.randomUUID().toString());
            chunk.setChunkData(chunkData);
            chunk.setChunkChecksum(calculateChecksum(chunkData));
            chunk.setIsLastChunk(i == totalChunks - 1);
            chunk.setTotalFileSize(fileSize);
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Assembles chunks back into complete file data
     */
    public static byte[] assembleChunks(List<FileChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("Chunks cannot be null or empty");
        }
        
        // Sort chunks by index
        chunks.sort((a, b) -> Integer.compare(a.getChunkIndex(), b.getChunkIndex()));
        
        // Validate chunk sequence
        validateChunkSequence(chunks);
        
        // Calculate total size
        long totalSize = chunks.stream().mapToLong(c -> c.getChunkSize()).sum();
        
        // Assemble data
        byte[] result = new byte[(int) totalSize];
        int offset = 0;
        
        for (FileChunkDto chunk : chunks) {
            byte[] chunkData = chunk.getChunkData();
            System.arraycopy(chunkData, 0, result, offset, chunkData.length);
            offset += chunkData.length;
        }
        
        return result;
    }
    
    /**
     * Validates that chunks form a complete sequence
     */
    public static void validateChunkSequence(List<FileChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("Chunks cannot be null or empty");
        }
        
        int expectedTotal = chunks.get(0).getTotalChunks();
        if (chunks.size() != expectedTotal) {
            throw new IllegalArgumentException("Expected " + expectedTotal + " chunks, but got " + chunks.size());
        }
        
        for (int i = 0; i < chunks.size(); i++) {
            FileChunkDto chunk = chunks.get(i);
            if (chunk.getChunkIndex() != i) {
                throw new IllegalArgumentException("Chunk index mismatch at position " + i + 
                    ": expected " + i + ", got " + chunk.getChunkIndex());
            }
            
            // Verify checksum
            String expectedChecksum = calculateChecksum(chunk.getChunkData());
            if (!expectedChecksum.equals(chunk.getChunkChecksum())) {
                throw new IllegalArgumentException("Checksum mismatch for chunk " + i);
            }
        }
        
        // Verify last chunk flag
        FileChunkDto lastChunk = chunks.get(chunks.size() - 1);
        if (!Boolean.TRUE.equals(lastChunk.getIsLastChunk())) {
            throw new IllegalArgumentException("Last chunk is not properly marked");
        }
    }
    
    /**
     * Calculate SHA-256 checksum of data
     */
    public static String calculateChecksum(byte[] data) {
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
    
    /**
     * Creates a chunk info summary for logging/debugging
     */
    public static String getChunkSummary(List<FileChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "No chunks";
        }
        
        long totalSize = chunks.stream().mapToLong(c -> c.getChunkSize()).sum();
        long avgChunkSize = totalSize / chunks.size();
        
        return String.format("Chunks: %d, Total size: %d bytes, Avg chunk size: %d bytes", 
            chunks.size(), totalSize, avgChunkSize);
    }
}
