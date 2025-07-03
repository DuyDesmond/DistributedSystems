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
        // Prevent duplicate conflict dialogs for the same file
        if (activeConflicts.computeIfAbsent(filePath, k -> new AtomicBoolean(false)).getAndSet(true)) {
            logger.debug("Conflict resolution already in progress for: {}", filePath);
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
                ConflictResolutionController.ConflictResolutionResult result = 
                    ConflictResolutionController.showConflictDialog(syncService, config, filePath);
                
                // Process the resolution result
                processResolutionResult(filePath, result);
                
            } catch (Exception e) {
                logger.error("Error in conflict resolution for: {}", filePath, e);
                
                // Fallback to automatic resolution
                performAutomaticResolution(filePath);
                
            } finally {
                // Mark conflict as resolved
                activeConflicts.remove(filePath);
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
                syncService.uploadResolvedFile(filePath);
                notifyResolutionComplete(filePath, "Local version uploaded");
            }
            case USE_SERVER -> {
                logger.info("User chose server version for: {}", filePath);
                syncService.queueFileSync(filePath, EnhancedSyncService.SyncOperation.DOWNLOAD);
                notifyResolutionComplete(filePath, "Server version downloaded");
            }
            case USE_MERGED -> {
                logger.info("User chose merged version for: {}", filePath);
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
}
