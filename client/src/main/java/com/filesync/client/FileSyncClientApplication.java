package com.filesync.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.filesync.client.config.ClientConfig;
import com.filesync.client.service.DatabaseService;
import com.filesync.client.service.EnhancedSyncService;
import com.filesync.client.service.FileWatchService;
import com.filesync.client.ui.MainController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FileSyncClientApplication extends Application {
    
    private ScheduledExecutorService executorService;
    private FileWatchService fileWatchService;
    private EnhancedSyncService syncService;
    private DatabaseService databaseService;
    private ClientConfig config;
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize configuration
        config = new ClientConfig();
        
        // Initialize services
        executorService = Executors.newScheduledThreadPool(4);
        databaseService = new DatabaseService("file_sync.db");
        syncService = new EnhancedSyncService(config, databaseService, executorService);
        fileWatchService = new FileWatchService(config, syncService, executorService);
        
        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);
        
        // Get controller and inject dependencies
        MainController controller = loader.getController();
        controller.initializeController(syncService, fileWatchService, config);
        
        primaryStage.setTitle("File Synchronizer");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();
        
        // Start file watching
        fileWatchService.start();
    }
    
    private void shutdown() {
        System.out.println("Shutting down File Sync Client...");
        
        try {
            // Stop file watching first
            if (fileWatchService != null) {
                fileWatchService.stop();
            }
            
            // Shutdown sync service (this should handle WebSocket and chunk download service)
            if (syncService != null) {
                syncService.stop();
            }
            
            // Close database connection
            if (databaseService != null) {
                databaseService.close();
            }
            
            // Shutdown executor service
            if (executorService != null) {
                executorService.shutdown();
                try {
                    // Wait for executor to finish
                    if (!executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            System.out.println("File Sync Client shutdown complete");
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}