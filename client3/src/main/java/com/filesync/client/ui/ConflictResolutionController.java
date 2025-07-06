package com.filesync.client.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filesync.client.config.ClientConfig;
import com.filesync.client.service.EnhancedSyncService;
import com.filesync.common.dto.FileDto;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Controller for conflict resolution UI
 */
public class ConflictResolutionController {
    
    private static final Logger logger = LoggerFactory.getLogger(ConflictResolutionController.class);
    
    
    @FXML private Label conflictFileLabel;
    @FXML private VBox textEditorPane;
    @FXML private VBox versionSelectorPane;
    @FXML private TextArea localVersionText;
    @FXML private TextArea serverVersionText;
    @FXML private TextArea mergedVersionText;
    @FXML private RadioButton useLocalVersionRadio;
    @FXML private RadioButton useServerVersionRadio;
    @FXML private Button resolveButton;
    @FXML private Button cancelButton;
    
    private EnhancedSyncService syncService;
    private ClientConfig config;
    private String conflictFilePath;
    private ConflictResolutionResult result;
    private Stage stage;
    
    // Text file extensions that can be edited
    private static final List<String> TEXT_EXTENSIONS = Arrays.asList(
        ".txt", ".md", ".java", ".py", ".js", ".ts", ".html", ".css", ".xml", ".json", 
        ".yml", ".yaml", ".properties", ".cfg", ".conf", ".log", ".sql", ".sh", ".bat",
        ".csv", ".ini", ".gitignore", ".dockerfile", ".gradle", ".maven", ".rb", ".php",
        ".go", ".rs", ".cpp", ".c", ".h", ".hpp", ".cs", ".vb", ".scala", ".kt"
    );
    
    // Document file extensions that can be edited as text
    private static final List<String> DOCUMENT_EXTENSIONS = Arrays.asList(
        ".doc", ".docx", ".rtf", ".odt", ".pages"
    );
    
    public enum ConflictResolutionResult {
        USE_LOCAL, USE_SERVER, USE_MERGED, CANCELLED
    }
    
    /**
     * Initialize the controller with conflict information
     */
    public void initializeConflict(EnhancedSyncService syncService, ClientConfig config, 
                                 String conflictFilePath, Stage stage) {
        this.syncService = syncService;
        this.config = config;
        this.conflictFilePath = conflictFilePath;
        this.stage = stage;
        this.result = ConflictResolutionResult.CANCELLED;
        
        setupUI();
        loadConflictData();
    }
    
    /**
     * Set up the UI based on file type
     */
    private void setupUI() {
        conflictFileLabel.setText("Conflict Resolution for: " + conflictFilePath);
        
        // Check if this is a text file or document that can be edited
        if (isEditableFile(conflictFilePath)) {
            setupTextEditor();
        } else {
            setupVersionSelector();
        }
        
        // Set up button handlers
        resolveButton.setOnAction(e -> handleResolve());
        cancelButton.setOnAction(e -> handleCancel());
    }
    
    /**
     * Check if file can be edited as text
     */
    private boolean isEditableFile(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();
        
        // For text files, we can directly edit them
        if (TEXT_EXTENSIONS.contains(extension)) {
            return true;
        }
        
        // For document files, we can attempt to read them as text for basic editing
        // Note: Complex documents may not edit well as plain text
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            try {
                Path path = Paths.get(config.getLocalSyncPath(), filePath);
                if (Files.exists(path)) {
                    // Try to read a small portion to see if it's readable text
                    byte[] bytes = Files.readAllBytes(path);
                    if (bytes.length > 0) {
                        // Simple heuristic: if file starts with readable characters, treat as editable
                        // This works for RTF and some simple document formats
                        String preview = new String(bytes, 0, Math.min(bytes.length, 200));
                        return preview.chars().limit(100)
                                .mapToObj(c -> (char) c)
                                .allMatch(c -> c >= 32 || Character.isWhitespace(c));
                    }
                }
            } catch (Exception e) {
                // If we can't read it, treat as binary
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Get file extension from path
     */
    private String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(lastDot) : "";
    }
    
    /**
     * Set up text editor for editable files
     */
    private void setupTextEditor() {
        textEditorPane.setVisible(true);
        versionSelectorPane.setVisible(false);
        
        // Configure text areas
        localVersionText.setEditable(false);
        serverVersionText.setEditable(false);
        mergedVersionText.setEditable(true);
        
        // Set up text area properties
        configureTextArea(localVersionText, "Local Version");
        configureTextArea(serverVersionText, "Server Version");
        configureTextArea(mergedVersionText, "Merged Version (Edit this)");
        
        resolveButton.setText("Save Merged Version");
    }
    
    /**
     * Configure text area properties
     */
    private void configureTextArea(TextArea textArea, String prompt) {
        textArea.setPromptText(prompt);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);
        VBox.setVgrow(textArea, Priority.ALWAYS);
    }
    
    /**
     * Set up version selector for binary files
     */
    private void setupVersionSelector() {
        textEditorPane.setVisible(false);
        versionSelectorPane.setVisible(true);
        
        // Set up radio button group
        ToggleGroup versionGroup = new ToggleGroup();
        useLocalVersionRadio.setToggleGroup(versionGroup);
        useServerVersionRadio.setToggleGroup(versionGroup);
        
        // Select local version by default
        useLocalVersionRadio.setSelected(true);
        
        resolveButton.setText("Resolve Conflict");
    }
    
    /**
     * Load conflict data from local and server versions
     */
    private void loadConflictData() {
        new Thread(() -> {
            try {
                // Load local version
                Path localPath = Paths.get(config.getLocalSyncPath(), conflictFilePath);
                String localContent = "";
                if (Files.exists(localPath)) {
                    if (isEditableFile(conflictFilePath)) {
                        localContent = Files.readString(localPath);
                    } else {
                        localContent = "Local file exists (" + Files.size(localPath) + " bytes)\n" +
                                     "Modified: " + Files.getLastModifiedTime(localPath);
                    }
                }
                
                // Load server version
                String serverContent = loadServerVersion();
                
                final String finalLocalContent = localContent;
                final String finalServerContent = serverContent;
                
                Platform.runLater(() -> {
                    if (isEditableFile(conflictFilePath)) {
                        localVersionText.setText(finalLocalContent);
                        serverVersionText.setText(finalServerContent);
                        
                        // Create initial merged version (local + server changes)
                        String mergedContent = createInitialMerge(finalLocalContent, finalServerContent);
                        mergedVersionText.setText(mergedContent);
                    } else {
                        updateBinaryFileInfo(finalLocalContent, finalServerContent);
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Error", "Failed to load conflict data: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Load server version of the file
     */
    private String loadServerVersion() {
        try {
            // Get server file content
            FileDto serverFile = syncService.downloadFileContent(conflictFilePath);
            if (serverFile != null && serverFile.getContent() != null) {
                if (isEditableFile(conflictFilePath)) {
                    return new String(serverFile.getContent());
                } else {
                    return "Server file exists (" + serverFile.getContent().length + " bytes)\n" +
                           "Checksum: " + serverFile.getChecksum();
                }
            }
            return "Server version not available";
        } catch (Exception e) {
            return "Error loading server version: " + e.getMessage();
        }
    }
    
    /**
     * Create initial merge of local and server content with better conflict markers
     */
    private String createInitialMerge(String localContent, String serverContent) {
        StringBuilder merged = new StringBuilder();
        
        // Add header with file information
        merged.append("# CONFLICT RESOLUTION FOR: ").append(conflictFilePath).append("\n");
        merged.append("# Edit the content below to resolve conflicts\n");
        merged.append("# Remove conflict markers when done\n\n");
        
        // Check if contents are significantly different
        if (localContent.trim().equals(serverContent.trim())) {
            merged.append("# No content differences found - files may have different metadata\n");
            merged.append(localContent);
        } else {
            // Create a more sophisticated merge
            merged.append("<<<<<<< LOCAL VERSION\n");
            merged.append(localContent);
            merged.append("\n=======\n");
            merged.append(serverContent);
            merged.append("\n>>>>>>> SERVER VERSION\n\n");
            
            // Add instructions
            merged.append("# Instructions:\n");
            merged.append("# 1. Review both versions above\n");
            merged.append("# 2. Edit the content below to combine the best of both\n");
            merged.append("# 3. Remove all conflict markers (<<<<<<, =======, >>>>>>)\n");
            merged.append("# 4. Click 'Save Merged Version' when done\n\n");
            
            // Provide a starting point for manual merge
            merged.append("# YOUR MERGED VERSION (edit this):\n");
            merged.append(localContent.length() > serverContent.length() ? localContent : serverContent);
        }
        
        return merged.toString();
    }
    
    /**
     * Update UI for binary file information
     */
    private void updateBinaryFileInfo(String localInfo, String serverInfo) {
        useLocalVersionRadio.setText("Use Local Version\n" + localInfo);
        useServerVersionRadio.setText("Use Server Version\n" + serverInfo);
    }
    
    /**
     * Handle resolve button click
     */
    @FXML
    private void handleResolve() {
        if (isEditableFile(conflictFilePath)) {
            handleTextFileResolution();
        } else {
            handleBinaryFileResolution();
        }
    }
    
    /**
     * Handle text file resolution
     */
    private void handleTextFileResolution() {
        String mergedContent = mergedVersionText.getText();
        
        if (mergedContent.trim().isEmpty()) {
            showAlert("Error", "Please provide content for the merged version.");
            return;
        }
        
        // Check if user has resolved conflict markers
        if (mergedContent.contains("<<<<<<<") || mergedContent.contains(">>>>>>>")) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Conflict Markers Found");
            alert.setHeaderText("Conflict markers still present");
            alert.setContentText("The merged version still contains conflict markers (<<<<<<< or >>>>>>>). " +
                                "Do you want to save anyway?");
            
            Optional<ButtonType> userChoice = alert.showAndWait();
            if (userChoice.isEmpty() || userChoice.get() != ButtonType.OK) {
                return;
            }
        }
        
        // Save merged content
        saveMergedContent(mergedContent);
        this.result = ConflictResolutionResult.USE_MERGED;
        closeDialog();
    }
    
    /**
     * Handle binary file resolution
     */
    private void handleBinaryFileResolution() {
        if (useLocalVersionRadio.isSelected()) {
            this.result = ConflictResolutionResult.USE_LOCAL;
        } else if (useServerVersionRadio.isSelected()) {
            this.result = ConflictResolutionResult.USE_SERVER;
        } else {
            showAlert("Error", "Please select a version to use.");
            return;
        }
        closeDialog();
    }
    
    /**
     * Save merged content to local file
     */
    private void saveMergedContent(String content) {
        try {
            Path localPath = Paths.get(config.getLocalSyncPath(), conflictFilePath);
            Files.writeString(localPath, content);
        } catch (IOException e) {
            showAlert("Error", "Failed to save merged content: " + e.getMessage());
        }
    }
    
    /**
     * Handle cancel button click
     */
    @FXML
    private void handleCancel() {
        this.result = ConflictResolutionResult.CANCELLED;
        closeDialog();
    }
    
    /**
     * Close the dialog
     */
    private void closeDialog() {
        if (stage != null) {
            stage.close();
        }
    }
    
    /**
     * Show alert dialog
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Get the resolution result
     */
    public ConflictResolutionResult getResult() {
        return result;
    }
    
    /**
     * Show conflict resolution dialog
     */
    public static ConflictResolutionResult showConflictDialog(EnhancedSyncService syncService, 
                                                            ClientConfig config, 
                                                            String conflictFilePath) {
        try {
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Resolve File Conflict");
            stage.setResizable(true);
            
            // Create UI programmatically since we don't have FXML
            ConflictResolutionController controller = new ConflictResolutionController();
            VBox root = createConflictResolutionUI(controller);
            
            Scene scene = new Scene(root, 1000, 700);
            stage.setScene(scene);
            
            controller.initializeConflict(syncService, config, conflictFilePath, stage);
            
            stage.showAndWait();
            return controller.getResult();
            
        } catch (RuntimeException e) {
            logger.error("Failed to show conflict dialog for: {}", conflictFilePath, e);
            return ConflictResolutionResult.CANCELLED;
        }
    }
    
    /**
     * Create conflict resolution UI programmatically
     */
    private static VBox createConflictResolutionUI(ConflictResolutionController controller) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        
        // File label
        Label fileLabel = new Label();
        fileLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        controller.conflictFileLabel = fileLabel;
        
        // Text editor pane for text files
        VBox textEditorPane = new VBox(10);
        
        Label instructionLabel = new Label("Edit the merged version below to resolve conflicts:");
        instructionLabel.setStyle("-fx-font-weight: bold;");
        
        // Create split view for text comparison
        HBox textComparisonBox = new HBox(10);
        
        VBox localBox = new VBox(5);
        localBox.getChildren().add(new Label("Local Version:"));
        TextArea localVersionText = new TextArea();
        localVersionText.setEditable(false);
        localVersionText.setPrefRowCount(10);
        localBox.getChildren().add(localVersionText);
        controller.localVersionText = localVersionText;
        
        VBox serverBox = new VBox(5);
        serverBox.getChildren().add(new Label("Server Version:"));
        TextArea serverVersionText = new TextArea();
        serverVersionText.setEditable(false);
        serverVersionText.setPrefRowCount(10);
        serverBox.getChildren().add(serverVersionText);
        controller.serverVersionText = serverVersionText;
        
        HBox.setHgrow(localBox, Priority.ALWAYS);
        HBox.setHgrow(serverBox, Priority.ALWAYS);
        textComparisonBox.getChildren().addAll(localBox, serverBox);
        
        // Merged version
        VBox mergedBox = new VBox(5);
        mergedBox.getChildren().add(new Label("Merged Version (Edit to resolve):"));
        TextArea mergedVersionText = new TextArea();
        mergedVersionText.setEditable(true);
        mergedVersionText.setPrefRowCount(15);
        VBox.setVgrow(mergedVersionText, Priority.ALWAYS);
        mergedBox.getChildren().add(mergedVersionText);
        controller.mergedVersionText = mergedVersionText;
        
        textEditorPane.getChildren().addAll(instructionLabel, textComparisonBox, mergedBox);
        VBox.setVgrow(textComparisonBox, Priority.SOMETIMES);
        VBox.setVgrow(mergedBox, Priority.ALWAYS);
        controller.textEditorPane = textEditorPane;
        
        // Version selector pane for binary files
        VBox versionSelectorPane = new VBox(20);
        versionSelectorPane.setPadding(new Insets(20));
        
        Label binaryLabel = new Label("This file cannot be merged automatically. Choose which version to keep:");
        binaryLabel.setStyle("-fx-font-weight: bold;");
        
        RadioButton useLocalRadio = new RadioButton();
        useLocalRadio.setStyle("-fx-font-size: 12px;");
        controller.useLocalVersionRadio = useLocalRadio;
        
        RadioButton useServerRadio = new RadioButton();
        useServerRadio.setStyle("-fx-font-size: 12px;");
        controller.useServerVersionRadio = useServerRadio;
        
        versionSelectorPane.getChildren().addAll(binaryLabel, useLocalRadio, useServerRadio);
        controller.versionSelectorPane = versionSelectorPane;
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button resolveButton = new Button("Resolve");
        resolveButton.setStyle("-fx-min-width: 100px;");
        controller.resolveButton = resolveButton;
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-min-width: 100px;");
        controller.cancelButton = cancelButton;
        
        buttonBox.getChildren().addAll(resolveButton, cancelButton);
        
        root.getChildren().addAll(fileLabel, textEditorPane, versionSelectorPane, buttonBox);
        VBox.setVgrow(textEditorPane, Priority.ALWAYS);
        
        return root;
    }
}
