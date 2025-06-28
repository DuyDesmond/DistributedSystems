package com.filesync.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.filesync.client.config.ClientConfig;
import com.filesync.client.service.FileWatchService;
import com.filesync.client.service.SyncService;
import com.filesync.client.ui.MainController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FileSyncClientApplication extends Application {
    
    private ScheduledExecutorService executorService;
    private FileWatchService fileWatchService;
    private SyncService syncService;
    private ClientConfig config;
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize configuration
        config = new ClientConfig();
        
        // Initialize services
        executorService = Executors.newScheduledThreadPool(4);
        syncService = new SyncService(config, executorService);
        fileWatchService = new FileWatchService(config, syncService, executorService);
        
        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);
        
        // Get controller and inject dependencies
        MainController controller = loader.getController();
        controller.initialize(syncService, fileWatchService, config);
        
        primaryStage.setTitle("File Synchronizer");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();
        
        // Start file watching
        fileWatchService.start();
    }
    
    private void shutdown() {
        if (fileWatchService != null) {
            fileWatchService.stop();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
