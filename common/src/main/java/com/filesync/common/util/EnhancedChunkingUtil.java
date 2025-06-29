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
 * Enhanced utility class for file chunking operations with improved performance and reliability
 */
public class EnhancedChunkingUtil {
    
    // Chunking configuration constants
    public static final long DEFAULT_CHUNK_SIZE_THRESHOLD = 10 * 1024 * 1024; // 10MB
    public static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
    public static final long MIN_CHUNK_SIZE = 1 * 1024 * 1024; // 1MB
    public static final long MAX_CHUNK_SIZE = 50 * 1024 * 1024; // 50MB
    public static final int MAX_CHUNKS_PER_FILE = 1000; // Maximum number of chunks per file
    
    /**
     * Determines if a file should be chunked based on its size
     */
    public static boolean shouldChunkFile(long fileSize) {
        return fileSize > DEFAULT_CHUNK_SIZE_THRESHOLD;
    }
    
    /**
     * Determines if a file should be chunked based on custom threshold
     */
    public static boolean shouldChunkFile(long fileSize, long threshold) {
        return fileSize > threshold;
    }
    
    /**
     * Calculates optimal chunk size based on file size
     * Ensures reasonable number of chunks while maintaining good performance
     */
    public static long calculateOptimalChunkSize(long fileSize) {
        // Start with default chunk size
        long chunkSize = DEFAULT_CHUNK_SIZE;
        
        // Calculate number of chunks with default size
        int chunksWithDefault = (int) Math.ceil((double) fileSize / DEFAULT_CHUNK_SIZE);
        
        // If too many chunks, increase chunk size
        if (chunksWithDefault > MAX_CHUNKS_PER_FILE) {
            chunkSize = (long) Math.ceil((double) fileSize / MAX_CHUNKS_PER_FILE);
            // Ensure chunk size doesn't exceed maximum
            chunkSize = Math.min(chunkSize, MAX_CHUNK_SIZE);
        }
        
        // Ensure chunk size is at least minimum
        chunkSize = Math.max(chunkSize, MIN_CHUNK_SIZE);
        
        return chunkSize;
    }
    
    /**
     * Chunks a file into multiple FileChunkDto objects with optimized size calculation
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
        return chunkFileData(Files.readAllBytes(filePath), fileId, uploadSessionId, chunkSize, filePath.toString());
    }
    
    /**
     * Chunks byte array data into multiple FileChunkDto objects
     */
    public static List<FileChunkDto> chunkData(byte[] data, String fileId) {
        return chunkData(data, fileId, null);
    }
    
    /**
     * Chunks byte array data into multiple FileChunkDto objects with custom session ID
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
        return chunkFileData(data, fileId, uploadSessionId, chunkSize, null);
    }
    
    /**
     * Internal method to chunk file data
     */
    private static List<FileChunkDto> chunkFileData(byte[] data, String fileId, String uploadSessionId, 
                                                   long chunkSize, String filePath) {
        if (uploadSessionId == null) {
            uploadSessionId = UUID.randomUUID().toString();
        }
        
        int totalChunks = (int) Math.ceil((double) data.length / chunkSize);
        List<FileChunkDto> chunks = new ArrayList<>(totalChunks);
        
        for (int i = 0; i < totalChunks; i++) {
            int start = (int) (i * chunkSize);
            int end = Math.min(start + (int) chunkSize, data.length);
            byte[] chunkData = Arrays.copyOfRange(data, start, end);
            
            FileChunkDto chunk = new FileChunkDto(fileId, uploadSessionId, i, totalChunks);
            chunk.setChunkId(UUID.randomUUID().toString());
            chunk.setChunkData(chunkData);
            chunk.setChunkChecksum(calculateChecksum(chunkData));
            chunk.setIsLastChunk(i == totalChunks - 1);
            chunk.setTotalFileSize((long) data.length);
            
            if (filePath != null) {
                chunk.setFilePath(filePath);
            }
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Validates that a list of chunks forms a complete sequence
     */
    public static void validateChunkSequence(List<FileChunkDto> chunks) throws IOException {
        if (chunks == null || chunks.isEmpty()) {
            throw new IOException("Chunk list is null or empty");
        }
        
        // Sort chunks by index to ensure correct order
        chunks.sort((a, b) -> Integer.compare(a.getChunkIndex(), b.getChunkIndex()));
        
        // Validate sequence completeness
        for (int i = 0; i < chunks.size(); i++) {
            FileChunkDto chunk = chunks.get(i);
            
            if (chunk.getChunkIndex() != i) {
                throw new IOException("Missing chunk at index " + i);
            }
            
            if (chunk.getChunkData() == null || chunk.getChunkData().length == 0) {
                throw new IOException("Chunk " + i + " has no data");
            }
            
            // Verify chunk checksum
            String expectedChecksum = calculateChecksum(chunk.getChunkData());
            if (!expectedChecksum.equals(chunk.getChunkChecksum())) {
                throw new IOException("Chunk " + i + " checksum mismatch");
            }
        }
        
        // Validate last chunk is marked correctly
        FileChunkDto lastChunk = chunks.get(chunks.size() - 1);
        if (!lastChunk.getIsLastChunk()) {
            throw new IOException("Last chunk is not marked as last");
        }
        
        // Validate total chunks count
        int expectedTotalChunks = chunks.size();
        for (FileChunkDto chunk : chunks) {
            if (chunk.getTotalChunks() != expectedTotalChunks) {
                throw new IOException("Inconsistent total chunks count in chunk " + chunk.getChunkIndex());
            }
        }
    }
    
    /**
     * Assembles chunks back into complete file data
     */
    public static byte[] assembleChunks(List<FileChunkDto> chunks) throws IOException {
        validateChunkSequence(chunks);
        
        // Calculate total size
        long totalSize = chunks.stream()
            .mapToLong(chunk -> chunk.getChunkData().length)
            .sum();
        
        // Validate against expected file size if available
        if (!chunks.isEmpty() && chunks.get(0).getTotalFileSize() != null) {
            long expectedSize = chunks.get(0).getTotalFileSize();
            if (totalSize != expectedSize) {
                throw new IOException("Assembled size (" + totalSize + ") doesn't match expected size (" + expectedSize + ")");
            }
        }
        
        // Assemble data
        byte[] assembledData = new byte[(int) totalSize];
        int offset = 0;
        
        for (FileChunkDto chunk : chunks) {
            byte[] chunkData = chunk.getChunkData();
            System.arraycopy(chunkData, 0, assembledData, offset, chunkData.length);
            offset += chunkData.length;
        }
        
        return assembledData;
    }
    
    /**
     * Calculates SHA-256 checksum of data
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
     * Estimates time to upload based on file size and connection speed
     */
    public static long estimateUploadTime(long fileSize, long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return -1; // Unknown
        }
        return fileSize / bytesPerSecond; // seconds
    }
    
    /**
     * Gets chunk statistics for a file
     */
    public static ChunkStatistics getChunkStatistics(long fileSize) {
        if (!shouldChunkFile(fileSize)) {
            return new ChunkStatistics(1, fileSize, fileSize, false);
        }
        
        long chunkSize = calculateOptimalChunkSize(fileSize);
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        
        return new ChunkStatistics(totalChunks, chunkSize, fileSize, true);
    }
    
    /**
     * Statistics about file chunking
     */
    public static class ChunkStatistics {
        private final int totalChunks;
        private final long chunkSize;
        private final long fileSize;
        private final boolean requiresChunking;
        
        public ChunkStatistics(int totalChunks, long chunkSize, long fileSize, boolean requiresChunking) {
            this.totalChunks = totalChunks;
            this.chunkSize = chunkSize;
            this.fileSize = fileSize;
            this.requiresChunking = requiresChunking;
        }
        
        public int getTotalChunks() { return totalChunks; }
        public long getChunkSize() { return chunkSize; }
        public long getFileSize() { return fileSize; }
        public boolean requiresChunking() { return requiresChunking; }
        
        public double getCompressionRatio() {
            return fileSize > 0 ? (double) chunkSize / fileSize : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ChunkStatistics{totalChunks=%d, chunkSize=%d, fileSize=%d, requiresChunking=%s}", 
                totalChunks, chunkSize, fileSize, requiresChunking);
        }
    }
}
