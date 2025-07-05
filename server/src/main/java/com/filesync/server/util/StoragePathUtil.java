package com.filesync.server.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for consistent storage path generation across all services
 */
public class StoragePathUtil {
    
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
    
    /**
     * Creates a standardized storage path for files
     * Pattern: {storageBasePath}/{userId}/{year}/{month}/{fileId}
     * Example: /app/storage/user123/2025/07/file456
     */
    public static String createStoragePath(String storageBasePath, String userId, String fileId) {
        LocalDateTime now = LocalDateTime.now();
        String year = now.format(YEAR_FORMATTER);
        String month = now.format(MONTH_FORMATTER);
        
        Path storagePath = Paths.get(storageBasePath, userId, year, month, fileId);
        
        // Create parent directories if they don't exist
        try {
            Files.createDirectories(storagePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory: " + storagePath.getParent(), e);
        }
        
        return storagePath.toString();
    }
    
    /**
     * Creates a standardized storage path for files with custom date
     * Pattern: {storageBasePath}/{userId}/{year}/{month}/{fileId}
     */
    public static String createStoragePath(String storageBasePath, String userId, String fileId, LocalDateTime dateTime) {
        String year = dateTime.format(YEAR_FORMATTER);
        String month = dateTime.format(MONTH_FORMATTER);
        
        Path storagePath = Paths.get(storageBasePath, userId, year, month, fileId);
        
        // Create parent directories if they don't exist
        try {
            Files.createDirectories(storagePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory: " + storagePath.getParent(), e);
        }
        
        return storagePath.toString();
    }
    
    /**
     * Creates a conflict resolution storage path
     * Pattern: {storageBasePath}/{userId}/{year}/{month}/conflicts/{fileId}_{clientId}_{timestamp}
     */
    public static String createConflictStoragePath(String storageBasePath, String userId, String fileId, 
                                                  String clientId, LocalDateTime dateTime) {
        String year = dateTime.format(YEAR_FORMATTER);
        String month = dateTime.format(MONTH_FORMATTER);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String conflictFileName = String.format("%s_%s_%s", fileId, clientId, timestamp);
        
        Path storagePath = Paths.get(storageBasePath, userId, year, month, "conflicts", conflictFileName);
        
        // Create parent directories if they don't exist
        try {
            Files.createDirectories(storagePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create conflict storage directory: " + storagePath.getParent(), e);
        }
        
        return storagePath.toString();
    }
    
    /**
     * Creates the base user directory path
     * Pattern: {storageBasePath}/{userId}
     */
    public static String createUserBasePath(String storageBasePath, String userId) {
        Path userPath = Paths.get(storageBasePath, userId);
        
        try {
            Files.createDirectories(userPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create user base directory: " + userPath, e);
        }
        
        return userPath.toString();
    }
    
    /**
     * Ensures a directory exists, creating it if necessary
     */
    public static void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }
    
    /**
     * Ensures a directory exists, creating it if necessary (String version)
     */
    public static void ensureDirectoryExists(String directoryPath) throws IOException {
        ensureDirectoryExists(Paths.get(directoryPath));
    }
}
