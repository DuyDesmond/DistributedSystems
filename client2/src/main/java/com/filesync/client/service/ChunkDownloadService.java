package com.filesync.client.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filesync.client.config.ClientConfig;

/**
 * Service for handling chunked file downloads with parallel processing
 */
public class ChunkDownloadService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChunkDownloadService.class);
    
    // Download configuration
    private static final long CHUNK_SIZE_THRESHOLD = 5 * 1024 * 1024; // 5MB - files larger than this will be chunked
    private static final long CHUNK_SIZE =  1024 * 1024; // 1MB per chunk
    private static final int MAX_CONCURRENT_DOWNLOADS = 3; // Maximum parallel chunk downloads
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second base delay
    
    private final ClientConfig config;
    private final CloseableHttpClient httpClient;
    private final ExecutorService downloadExecutor;
    private final Semaphore downloadSemaphore;
    
    // Download progress tracking
    private final ConcurrentHashMap<String, DownloadProgress> downloadProgressMap = new ConcurrentHashMap<>();
    
    public ChunkDownloadService(ClientConfig config) {
        this.config = config;
        this.httpClient = HttpClients.createDefault();
        this.downloadExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_DOWNLOADS);
        this.downloadSemaphore = new Semaphore(MAX_CONCURRENT_DOWNLOADS);
    }
    
    /**
     * Download file with chunking support for large files
     */
    public void downloadFile(String fileId, String fileName, long fileSize, Path localPath) throws IOException {
        if (shouldChunkDownload(fileSize)) {
            downloadFileWithChunking(fileId, fileName, fileSize, localPath);
        } else {
            downloadFileDirectly(fileId, localPath);
        }
    }
    
    /**
     * Check if file should be downloaded in chunks
     */
    private boolean shouldChunkDownload(long fileSize) {
        return fileSize >= CHUNK_SIZE_THRESHOLD;
    }
    
    /**
     * Download large file using chunked approach with parallel downloads
     */
    private void downloadFileWithChunking(String fileId, String fileName, long fileSize, Path localPath) throws IOException {
        logger.info("Starting chunked download for large file: {} ({} bytes)", fileName, fileSize);
        
        // Calculate chunk count
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        logger.info("File will be downloaded in {} chunks", totalChunks);
        
        // Initialize progress tracking
        DownloadProgress progress = new DownloadProgress(totalChunks, fileSize);
        downloadProgressMap.put(fileId, progress);
        
        // Create temporary file for writing chunks
        Path tempFile = localPath.resolveSibling(localPath.getFileName() + ".downloading");
        Files.createDirectories(tempFile.getParent());
        
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(tempFile.toFile(), "rw")) {
            randomAccessFile.setLength(fileSize); // Pre-allocate file space
            
            // Create list of download tasks
            List<CompletableFuture<Void>> downloadTasks = new ArrayList<>();
            
            for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                final int currentChunk = chunkIndex;
                final long chunkStart = currentChunk * CHUNK_SIZE;
                final long chunkEnd = Math.min(chunkStart + CHUNK_SIZE - 1, fileSize - 1);
                final long chunkSizeActual = chunkEnd - chunkStart + 1;
                
                CompletableFuture<Void> downloadTask = CompletableFuture.runAsync(() -> {
                    try {
                        downloadChunkWithRetry(fileId, currentChunk, chunkStart, chunkEnd, randomAccessFile, progress);
                        logger.debug("Downloaded chunk {}/{} for file: {} ({} bytes)", 
                            currentChunk + 1, totalChunks, fileName, chunkSizeActual);
                    } catch (IOException e) {
                        logger.error("Failed to download chunk {} for file: {}", currentChunk, fileName, e);
                        progress.markFailed();
                        throw new RuntimeException("Chunk download failed", e);
                    }
                }, downloadExecutor);
                
                downloadTasks.add(downloadTask);
            }
            
            // Wait for all chunks to complete
            CompletableFuture.allOf(downloadTasks.toArray(CompletableFuture[]::new)).join();
            
            if (progress.isFailed()) {
                throw new IOException("One or more chunks failed to download");
            }
            
            // Verify file integrity (optional - could add checksum verification here)
            logger.info("All chunks downloaded successfully for file: {}", fileName);
            
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Chunked download failed for file: " + fileName, e);
        }
        
        // Move completed file to final location
        Files.move(tempFile, localPath);
        downloadProgressMap.remove(fileId);
        
        logger.info("Completed chunked download for file: {}", fileName);
    }
    
    /**
     * Download a single chunk with retry logic
     */
    private void downloadChunkWithRetry(String fileId, int chunkIndex, long rangeStart, long rangeEnd, 
                                       RandomAccessFile file, DownloadProgress progress) throws IOException {
        
        try {
            downloadSemaphore.acquire();

            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    downloadChunk(fileId, rangeStart, rangeEnd, file);
                    progress.incrementCompleted();
                    return; // Success
                } catch (IOException e) {
                    if (attempt == MAX_RETRY_ATTEMPTS) {
                        throw e; // Final attempt failed
                    }
                    
                    logger.warn("Chunk download attempt {} failed for chunk {}, retrying: {}", 
                        attempt, chunkIndex, e.getMessage());
                    
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrupted", ie);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        } finally {
            downloadSemaphore.release();
        }
    }
    
    /**
     * Download a single chunk using HTTP Range requests
     */
    private void downloadChunk(String fileId, long rangeStart, long rangeEnd, 
                              RandomAccessFile file) throws IOException {
        HttpGet get = new HttpGet(config.getServerUrl() + "/api/files/" + fileId + "/download-chunked");
        get.setHeader("Authorization", "Bearer " + config.getToken());
        get.setHeader("Range", "bytes=" + rangeStart + "-" + rangeEnd);
        
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            if (response.getCode() == 206) { // Partial Content
                try (InputStream inputStream = response.getEntity().getContent()) {
                    synchronized (file) {
                        file.seek(rangeStart);
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            file.write(buffer, 0, bytesRead);
                        }
                    }
                }
            } else {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (ParseException e) {
                    responseBody = "Failed to parse response";
                }
                throw new IOException("Chunk download failed with status " + response.getCode() + ": " + responseBody);
            }
        }
    }
    
    /**
     * Download small file directly without chunking
     */
    private void downloadFileDirectly(String fileId, Path localPath) throws IOException {
        logger.info("Starting direct download for file: {}", localPath.getFileName());
        
        HttpGet get = new HttpGet(config.getServerUrl() + "/api/files/" + fileId + "/download");
        get.setHeader("Authorization", "Bearer " + config.getToken());
        
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            if (response.getCode() == 200) {
                Files.createDirectories(localPath.getParent());
                Files.copy(response.getEntity().getContent(), localPath);
                logger.info("File downloaded successfully: {}", localPath.getFileName());
            } else {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (ParseException e) {
                    responseBody = "Failed to parse response";
                }
                throw new IOException("File download failed with status " + response.getCode() + ": " + responseBody);
            }
        }
    }
    
    /**
     * Get download progress for a file
     */
    public DownloadProgress getDownloadProgress(String fileId) {
        return downloadProgressMap.get(fileId);
    }
    
    /**
     * Cancel ongoing download
     */
    public void cancelDownload(String fileId) {
        DownloadProgress progress = downloadProgressMap.get(fileId);
        if (progress != null) {
            progress.markFailed();
            downloadProgressMap.remove(fileId);
        }
    }
    
    /**
     * Shutdown the download service
     */
    public void shutdown() {
        downloadExecutor.shutdown();
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.error("Error closing HTTP client", e);
        }
    }
    
    /**
     * Class to track download progress
     */
    public static class DownloadProgress {
        private final int totalChunks;
        private final long totalSize;
        private final AtomicInteger completedChunks = new AtomicInteger(0);
        private volatile boolean failed = false;
        
        public DownloadProgress(int totalChunks, long totalSize) {
            this.totalChunks = totalChunks;
            this.totalSize = totalSize;
        }
        
        public void incrementCompleted() {
            completedChunks.incrementAndGet();
        }
        
        public double getProgress() {
            return totalChunks > 0 ? (double) completedChunks.get() / totalChunks * 100.0 : 0.0;
        }
        
        public boolean isComplete() {
            return completedChunks.get() >= totalChunks && !failed;
        }
        
        public boolean isFailed() {
            return failed;
        }
        
        public void markFailed() {
            this.failed = true;
        }
        
        public int getTotalChunks() { return totalChunks; }
        public int getCompletedChunks() { return completedChunks.get(); }
        public long getTotalSize() { return totalSize; }
    }
}
