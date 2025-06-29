package com.filesync.client.ui;

import java.io.File;

import com.filesync.client.config.ClientConfig;
import com.filesync.client.service.EnhancedSyncService;
import com.filesync.client.service.FileWatchService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Main UI Controller for the File Sync Client
 */
public class MainController {
    
    @FXML private VBox loginPane;
    @FXML private VBox mainPane;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;
    @FXML private TextField serverUrlField;
    @FXML private TextField syncPathField;
    @FXML private Label statusLabel;
    @FXML private Label userLabel;
    @FXML private TextArea logArea;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Button logoutButton;
    @FXML private Button browseSyncPathButton;
    @FXML private Button uploadFileButton;
    
    private EnhancedSyncService syncService;
    private FileWatchService fileWatchService;
    private ClientConfig config;
    
    /**
     * Standard FXML initialize method (called automatically when FXML is loaded)
     */
    @FXML
    public void initialize() {
        // This method is called automatically when the FXML is loaded
        // Dependencies will be injected later via the custom initialize method
    }
    
    /**
     * Custom initialize method for dependency injection
     */
    public void initializeController(EnhancedSyncService syncService, FileWatchService fileWatchService, ClientConfig config) {
        this.syncService = syncService;
        this.fileWatchService = fileWatchService;
        this.config = config;
        
        setupUI();
        updateUIState();
    }
    
    private void setupUI() {
        // Initialize fields with config values
        if (serverUrlField != null) {
            serverUrlField.setText(config.getServerUrl());
        }
        if (syncPathField != null) {
            syncPathField.setText(config.getLocalSyncPath());
        }
        
        // Set up event handlers
        if (loginButton != null) {
            loginButton.setOnAction(e -> handleLogin());
        }
        if (registerButton != null) {
            registerButton.setOnAction(e -> handleRegister());
        }
        if (logoutButton != null) {
            logoutButton.setOnAction(e -> handleLogout());
        }
        if (browseSyncPathButton != null) {
            browseSyncPathButton.setOnAction(e -> handleBrowseSyncPath());
        }
        if (uploadFileButton != null) {
            uploadFileButton.setOnAction(e -> handleUploadFile());
        }
        
        // Update log area periodically
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText("File Sync Client started\n");
            }
        });
    }
    
    private void updateUIState() {
        boolean loggedIn = syncService.isLoggedIn();
        
        if (loginPane != null) {
            loginPane.setVisible(!loggedIn);
        }
        if (mainPane != null) {
            mainPane.setVisible(loggedIn);
        }
        
        if (loggedIn) {
            if (userLabel != null) {
                userLabel.setText("Logged in as: " + config.getUsername());
            }
            if (statusLabel != null) {
                statusLabel.setText("Status: Connected - Watching: " + config.getLocalSyncPath());
            }
        } else {
            if (statusLabel != null) {
                statusLabel.setText("Status: Not logged in");
            }
        }
    }
    
    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please enter username and password");
            return;
        }
        
        // Update server URL if changed
        config.setServerUrl(serverUrlField.getText());
        config.setLocalSyncPath(syncPathField.getText());
        config.saveConfig();
        
        // Disable login button during authentication
        loginButton.setDisable(true);
        loginButton.setText("Logging in...");
        
        // Perform login in background thread
        new Thread(() -> {
            boolean success = syncService.login(username, password);
            
            Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginButton.setText("Login");
                
                if (success) {
                    updateUIState();
                    appendLog("Login successful");
                } else {
                    showAlert("Error", "Login failed. Please check your credentials.");
                }
            });
        }).start();
    }
    
    @FXML
    private void handleRegister() {
        String username = usernameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please fill in all fields");
            return;
        }
        
        registerButton.setDisable(true);
        registerButton.setText("Registering...");
        
        new Thread(() -> {
            boolean success = syncService.register(username, email, password);
            
            Platform.runLater(() -> {
                registerButton.setDisable(false);
                registerButton.setText("Register");
                
                if (success) {
                    updateUIState();
                    appendLog("Registration and login successful");
                } else {
                    showAlert("Error", "Registration failed. Please try again.");
                }
            });
        }).start();
    }
    
    @FXML
    private void handleLogout() {
        config.setToken(null);
        config.setRefreshToken(null);
        config.setUsername(null);
        config.saveConfig();
        
        // Clear form fields
        usernameField.clear();
        passwordField.clear();
        emailField.clear();
        
        updateUIState();
        appendLog("Logged out");
    }
    
    @FXML
    private void handleBrowseSyncPath() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Sync Directory");
        
        // Set initial directory
        File currentPath = new File(config.getLocalSyncPath());
        if (currentPath.exists()) {
            directoryChooser.setInitialDirectory(currentPath);
        }
        
        Stage stage = (Stage) browseSyncPathButton.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);
        
        if (selectedDirectory != null) {
            String newPath = selectedDirectory.getAbsolutePath();
            syncPathField.setText(newPath);
            config.setLocalSyncPath(newPath);
            config.saveConfig();
            
            appendLog("Sync path changed to: " + newPath);
            
            // Restart file watching with new path
            if (fileWatchService.isRunning()) {
                fileWatchService.stop();
                fileWatchService.start();
            }
        }
    }

    @FXML
    private void handleUploadFile() {
        if (!syncService.isLoggedIn()) {
            showAlert("Error", "Please log in first");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        
        // Allow selection from anywhere - no initial directory restriction
        Stage stage = (Stage) uploadFileButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        
        if (selectedFile != null) {
            String syncDir = config.getLocalSyncPath();
            String filePath = selectedFile.getAbsolutePath();
            
            // Check if file is already within sync directory
            if (filePath.startsWith(syncDir)) {
                // File is already in sync directory - use existing logic
                uploadFileButton.setDisable(true);
                uploadFileButton.setText("Uploading...");
                
                new Thread(() -> {
                    try {
                        syncService.queueFileForUpload(selectedFile.toPath());
                        
                        Platform.runLater(() -> {
                            uploadFileButton.setDisable(false);
                            uploadFileButton.setText("Upload File ");
                            appendLog("File queued for upload: " + selectedFile.getName());
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            uploadFileButton.setDisable(false);
                            uploadFileButton.setText("Upload File ");
                            showAlert("Error", "Failed to queue file for upload: " + e.getMessage());
                        });
                    }
                }).start();
            } else {
                // File is outside sync directory - offer to copy it
                String fileName = selectedFile.getName();
                String message = "This file is outside your sync directory.\n" +
                               "Do you want to copy it to the sync directory and upload it?\n\n" +
                               "File: " + fileName + "\n" +
                               "Will be copied to: " + syncDir;
                
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Copy and Upload File");
                confirmAlert.setHeaderText("File Outside Sync Directory");
                confirmAlert.setContentText(message);
                
                confirmAlert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        uploadFileButton.setDisable(true);
                        uploadFileButton.setText("Copying & Uploading...");
                        
                        new Thread(() -> {
                            try {
                                // Use the new external file upload method
                                syncService.uploadExternalFile(selectedFile.toPath());
                                
                                Platform.runLater(() -> {
                                    uploadFileButton.setDisable(false);
                                    uploadFileButton.setText("Upload File ");
                                    appendLog("File copied and queued for upload: " + fileName);
                                    showAlert("Success", "File copied to sync directory and queued for upload!");
                                });
                            } catch (Exception e) {
                                Platform.runLater(() -> {
                                    uploadFileButton.setDisable(false);
                                    uploadFileButton.setText("Upload File ");
                                    showAlert("Error", "Failed to copy and upload file: " + e.getMessage());
                                });
                            }
                        }).start();
                    }
                });
            }
        }
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void appendLog(String message) {
        if (logArea != null) {
            Platform.runLater(() -> {
                logArea.appendText(java.time.LocalTime.now() + ": " + message + "\n");
            });
        }
    }
}
