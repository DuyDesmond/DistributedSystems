package com.filesync.client.service;

import com.filesync.client.config.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * File Watch Service for monitoring local file changes
 */
public class FileWatchService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileWatchService.class);
    
    private final ClientConfig config;
    private final EnhancedSyncService syncService;
    private final ScheduledExecutorService executorService;
    
    private WatchService watchService;
    private boolean running = false;
    
    public FileWatchService(ClientConfig config, EnhancedSyncService syncService, ScheduledExecutorService executorService) {
        this.config = config;
        this.syncService = syncService;
        this.executorService = executorService;
    }
    
    public void start() {
        if (running) return;
        
        try {
            config.initializeSyncDirectory();
            watchService = FileSystems.getDefault().newWatchService();
            
            Path syncPath = Paths.get(config.getLocalSyncPath());
            registerDirectoryRecursively(syncPath);
            
            running = true;
            
            // Start watching in a separate thread
            executorService.submit(this::watchForChanges);
            
            // Perform initial scan of existing files after a short delay to allow sync service to be ready
            executorService.schedule(this::performInitialScan, 3, TimeUnit.SECONDS);
            
            logger.info("File watch service started for path: {}", config.getLocalSyncPath());
            
        } catch (IOException e) {
            logger.error("Failed to start file watch service", e);
            throw new RuntimeException("Failed to start file watch service", e);
        }
    }
    
    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Error closing watch service", e);
            }
        }
        logger.info("File watch service stopped");
    }
    
    private void registerDirectoryRecursively(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                logger.debug("Registering directory for watching: {}", dir);
                dir.register(watchService, 
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                );
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private void watchForChanges() {
        while (running) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path fileName = pathEvent.context();
                        Path directory = (Path) key.watchable();
                        Path fullPath = directory.resolve(fileName);
                        
                        logger.debug("File change detected: {} - {}", kind.name(), fullPath);
                        
                        // Handle the file change
                        handleFileChange(kind, fullPath);
                        
                        // If a new directory was created, register it for watching
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                            registerDirectoryRecursively(fullPath);
                        }
                    }
                    
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                logger.error("Error while watching for file changes", e);
            }
        }
    }
    
    private void handleFileChange(WatchEvent.Kind<?> kind, Path filePath) {
        // Skip temporary files and hidden files
        String fileName = filePath.getFileName().toString();
        if (fileName.startsWith(".") || fileName.endsWith(".tmp") || fileName.endsWith("~")) {
            return;
        }
        
        // Check if sync service is ready and authenticated
        if (!syncService.isLoggedIn()) {
            logger.warn("File change detected but user not authenticated: {} - {}", kind.name(), filePath);
            return;
        }
        
        // Queue the sync operation
        executorService.submit(() -> {
            try {
                if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (Files.isRegularFile(filePath)) {
                        logger.info("Queuing file for upload: {}", filePath);
                        syncService.queueFileForUpload(filePath);
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    logger.info("Queuing file for deletion: {}", filePath);
                    syncService.queueFileForDeletion(filePath);
                }
            } catch (Exception e) {
                logger.error("Error handling file change for: " + filePath, e);
            }
        });
    }
    
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Perform initial scan of sync directory to queue existing files for upload
     */
    private void performInitialScan() {
        if (!running) {
            return;
        }
        
        logger.info("Starting initial scan of sync directory...");
        
        // Check if sync service is ready and authenticated
        if (!syncService.isLoggedIn()) {
            logger.warn("Initial scan skipped - user not authenticated. Please login first.");
            return;
        }
        
        try {
            Path syncPath = Paths.get(config.getLocalSyncPath());
            
            Files.walkFileTree(syncPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile()) {
                        // Skip temporary files and hidden files
                        String fileName = file.getFileName().toString();
                        if (!fileName.startsWith(".") && !fileName.endsWith(".tmp") && !fileName.endsWith("~")) {
                            logger.debug("Queuing existing file for upload: {}", file);
                            
                            // Queue the file for upload
                            executorService.submit(() -> {
                                try {
                                    syncService.queueFileForUpload(file);
                                } catch (Exception e) {
                                    logger.error("Error queuing existing file for upload: " + file, e);
                                }
                            });
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    logger.warn("Failed to visit file during initial scan: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
            
            logger.info("Initial scan completed");
            
        } catch (IOException e) {
            logger.error("Error during initial scan of sync directory", e);
        }
    }
}
