package com.filesync.client.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filesync.client.config.ClientConfig;
import com.filesync.client.ui.ConflictResolutionController;
import com.filesync.client.ui.MainController;

import javafx.application.Platform;

/**
 * Service to manage file conflicts and coordinate resolution across the application
 */
public class ConflictManager {
    private static final Logger logger = LoggerFactory.getLogger(ConflictManager.class);
    
    private final EnhancedSyncService syncService;
    private final ClientConfig config;
    private MainController mainController;
    
    // Track active conflicts to prevent duplicate dialogs
    private final ConcurrentHashMap<String, AtomicBoolean> activeConflicts = new ConcurrentHashMap<>();
    
    // Track recently resolved files to prevent immediate re-triggering
    private final ConcurrentHashMap<String, Long> recentlyResolvedFiles = new ConcurrentHashMap<>();
    // Track recently uploaded files to prevent metadata conflicts
    private final ConcurrentHashMap<String, Long> recentlyUploadedFiles = new ConcurrentHashMap<>();
    private static final long CONFLICT_RESOLUTION_GRACE_PERIOD_MS = 15000; // 15 seconds - increased for better robustness
    private static final long UPLOAD_GRACE_PERIOD_MS = 10000; // 10 seconds for upload grace period
    
    public ConflictManager(EnhancedSyncService syncService, ClientConfig config) {
        this.syncService = syncService;
        this.config = config;
    }
    
    /**
     * Set the main controller for notifications
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Handle a file conflict with UI resolution
     */
    public void handleConflict(String filePath) {
        logger.debug("=== CONFLICT HANDLING START for: {} ===", filePath);
        
        // Check if file was recently resolved - prevent immediate re-triggering
        if (isInGracePeriod(filePath)) {
            logger.debug("File {} is in conflict resolution grace period, skipping conflict handling", filePath);
            logger.debug("=== CONFLICT HANDLING SKIPPED (GRACE PERIOD) for: {} ===", filePath);
            return;
        }
        
        // Prevent duplicate conflict dialogs for the same file
        if (activeConflicts.computeIfAbsent(filePath, k -> new AtomicBoolean(false)).getAndSet(true)) {
            logger.debug("Conflict resolution already in progress for: {}", filePath);
            logger.debug("=== CONFLICT HANDLING SKIPPED (ACTIVE) for: {} ===", filePath);
            return;
        }
        
        logger.info("Starting conflict resolution for: {}", filePath);
        
        // Show notification to user
        if (mainController != null) {
            mainController.showConflictNotification(filePath);
        }
        
        // Show conflict resolution dialog on JavaFX Application Thread
        Platform.runLater(() -> {
            try {
                logger.debug("Showing conflict dialog for: {}", filePath);
                ConflictResolutionController.ConflictResolutionResult result = 
                    ConflictResolutionController.showConflictDialog(syncService, config, filePath);
                
                logger.debug("Conflict resolution result for {}: {}", filePath, result);
                // Process the resolution result
                processResolutionResult(filePath, result);
                
            } catch (Exception e) {
                logger.error("Error in conflict resolution for: {}", filePath, e);
                
                // Fallback to automatic resolution
                performAutomaticResolution(filePath);
                
            } finally {
                // Mark conflict as resolved
                activeConflicts.remove(filePath);
                logger.debug("=== CONFLICT HANDLING END for: {} ===", filePath);
            }
        });
    }
    
    /**
     * Process the user's conflict resolution choice
     */
    private void processResolutionResult(String filePath, ConflictResolutionController.ConflictResolutionResult result) {
        switch (result) {
            case USE_LOCAL -> {
                logger.info("User chose local version for: {}", filePath);
                markFileAsRecentlyResolved(filePath); // Mark BEFORE upload to prevent race condition
                syncService.uploadResolvedFile(filePath);
                notifyResolutionComplete(filePath, "Local version uploaded");
            }
            case USE_SERVER -> {
                logger.info("User chose server version for: {}", filePath);
                markFileAsRecentlyResolved(filePath); // Mark BEFORE download to prevent race condition
                syncService.queueFileSync(filePath, EnhancedSyncService.SyncOperation.DOWNLOAD);
                notifyResolutionComplete(filePath, "Server version downloaded");
            }
            case USE_MERGED -> {
                logger.info("User chose merged version for: {}", filePath);
                markFileAsRecentlyResolved(filePath); // Mark BEFORE upload to prevent race condition
                syncService.uploadResolvedFile(filePath);
                notifyResolutionComplete(filePath, "Merged version uploaded");
            }
            case CANCELLED -> {
                logger.info("User cancelled conflict resolution for: {}", filePath);
                notifyResolutionComplete(filePath, "Conflict resolution cancelled");
            }
        }
    }
    
    /**
     * Perform automatic conflict resolution when UI fails
     */
    private void performAutomaticResolution(String filePath) {
        logger.info("Performing automatic conflict resolution for: {}", filePath);
        
        // Simple Last-Write-Wins strategy
        try {
            // Mark file as resolved BEFORE upload to prevent race condition
            markFileAsRecentlyResolved(filePath);
            // Upload local version as the resolution
            syncService.uploadResolvedFile(filePath);
            notifyResolutionComplete(filePath, "Automatic resolution: local version used");
            
        } catch (Exception e) {
            logger.error("Automatic conflict resolution failed for: {}", filePath, e);
            notifyResolutionComplete(filePath, "Conflict resolution failed");
        }
    }
    
    /**
     * Notify that conflict resolution is complete
     */
    private void notifyResolutionComplete(String filePath, String message) {
        logger.info("Conflict resolution complete for {}: {}", filePath, message);
        
        if (mainController != null) {
            Platform.runLater(() -> {
                mainController.appendLog("Conflict resolved for " + filePath + ": " + message);
            });
        }
    }
    
    /**
     * Check if a file has an active conflict resolution
     */
    public boolean hasActiveConflict(String filePath) {
        AtomicBoolean active = activeConflicts.get(filePath);
        return active != null && active.get();
    }
    
    /**
     * Clear all active conflicts (useful for cleanup)
     */
    public void clearActiveConflicts() {
        activeConflicts.clear();
        logger.info("Cleared all active conflicts");
    }
    
    /**
     * Mark a file as recently resolved to prevent immediate re-triggering
     */
    public void markFileAsRecentlyResolved(String filePath) {
        String normalizedPath = normalizePath(filePath);
        recentlyResolvedFiles.put(normalizedPath, System.currentTimeMillis());
        logger.debug("Marked file as recently resolved: {} (normalized: {})", filePath, normalizedPath);
    }
    
    /**
     * Mark a file as recently uploaded to prevent metadata conflicts
     */
    public void markFileAsRecentlyUploaded(String filePath) {
        String normalizedPath = normalizePath(filePath);
        recentlyUploadedFiles.put(normalizedPath, System.currentTimeMillis());
        logger.debug("Marked file as recently uploaded: {} (normalized: {})", filePath, normalizedPath);
    }
    
    /**
     * Check if a file is in the grace period after conflict resolution
     */
    private boolean isInGracePeriod(String filePath) {
        String normalizedPath = normalizePath(filePath);
        Long resolveTime = recentlyResolvedFiles.get(normalizedPath);
        if (resolveTime == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        boolean inGracePeriod = (currentTime - resolveTime) < CONFLICT_RESOLUTION_GRACE_PERIOD_MS;
        
        // Clean up expired entries
        if (!inGracePeriod) {
            recentlyResolvedFiles.remove(normalizedPath);
        }
        
        return inGracePeriod;
    }
    
    /**
     * Check if a file was recently uploaded (to prevent metadata conflicts)
     */
    private boolean wasRecentlyUploaded(String filePath) {
        String normalizedPath = normalizePath(filePath);
        Long uploadTime = recentlyUploadedFiles.get(normalizedPath);
        if (uploadTime == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        boolean isRecent = (currentTime - uploadTime) < UPLOAD_GRACE_PERIOD_MS;
        
        // Clean up expired entries
        if (!isRecent) {
            recentlyUploadedFiles.remove(normalizedPath);
        }
        
        return isRecent;
    }
    
    /**
     * Normalize file path for consistent comparison
     */
    private String normalizePath(String filePath) {
        if (filePath == null) {
            return null;
        }
        // Convert to forward slashes and remove any leading/trailing slashes
        return filePath.replace("\\", "/").replaceFirst("^/+", "").replaceFirst("/+$", "");
    }
    
    /**
     * Check if a file should be excluded from conflict detection due to recent resolution or upload
     */
    public boolean shouldSkipConflictCheck(String filePath) {
        return isInGracePeriod(filePath) || wasRecentlyUploaded(filePath);
    }
}
