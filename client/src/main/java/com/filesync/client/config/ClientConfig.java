package com.filesync.client.config;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Client Configuration
 */
public class ClientConfig {
    
    private static final String CONFIG_FILE = "client.properties";
    private Properties properties;
    
    // Default values
    private String serverUrl = "http://localhost:8080/api";
    private String localSyncPath = "./sync";
    private String clientId;
    private String username;
    private String token;
    private String refreshToken;
    private int syncInterval = 30; // seconds
    
    public ClientConfig() {
        this.properties = new Properties();
        this.clientId = java.util.UUID.randomUUID().toString();
        loadConfig();
    }
    
    private void loadConfig() {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            properties.load(fis);
            
            serverUrl = properties.getProperty("server.url", serverUrl);
            localSyncPath = properties.getProperty("sync.path", localSyncPath);
            clientId = properties.getProperty("client.id", clientId);
            username = properties.getProperty("user.username", username);
            token = properties.getProperty("auth.token", token);
            refreshToken = properties.getProperty("auth.refresh_token", refreshToken);
            syncInterval = Integer.parseInt(properties.getProperty("sync.interval", String.valueOf(syncInterval)));
            
        } catch (IOException e) {
            // Config file doesn't exist, use defaults
            saveConfig();
        }
    }
    
    public void saveConfig() {
        try {
            properties.setProperty("server.url", serverUrl);
            properties.setProperty("sync.path", localSyncPath);
            properties.setProperty("client.id", clientId);
            if (username != null) properties.setProperty("user.username", username);
            if (token != null) properties.setProperty("auth.token", token);
            if (refreshToken != null) properties.setProperty("auth.refresh_token", refreshToken);
            properties.setProperty("sync.interval", String.valueOf(syncInterval));
            
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                properties.store(fos, "File Sync Client Configuration");
            }
        } catch (IOException e) {
            System.err.println("Failed to save configuration: " + e.getMessage());
        }
    }
    
    public void initializeSyncDirectory() {
        try {
            Files.createDirectories(Paths.get(localSyncPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create sync directory: " + localSyncPath, e);
        }
    }
    
    // Getters and setters
    public String getServerUrl() {
        return serverUrl;
    }
    
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }
    
    public String getLocalSyncPath() {
        return localSyncPath;
    }
    
    public void setLocalSyncPath(String localSyncPath) {
        this.localSyncPath = localSyncPath;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public int getSyncInterval() {
        return syncInterval;
    }
    
    public void setSyncInterval(int syncInterval) {
        this.syncInterval = syncInterval;
    }
    
    public String getSyncFolder() {
        return localSyncPath;
    }
}
