package com.filesync.client.config;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client Configuration
 */
public class ClientConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ClientConfig.class);
    private static final String CONFIG_FILE = "client.properties";
    private Properties properties;
    
    // Default values
    private String serverUrl = "http://localhost:8080/api";
    private String localSyncPath = "./sync";
    private String clientId;
    private String username;
    private String token;
    private String refreshToken;
    private int syncInterval = 10; // seconds
    
    public ClientConfig() {
        this.properties = new Properties();
        this.clientId = generateDefaultClientId();
        loadConfig();
    }
    
    private void loadConfig() {
        try {
            // Check if client.properties exists as a directory (Docker volume issue)
            Path configPath = Paths.get(CONFIG_FILE);
            if (Files.isDirectory(configPath)) {
                logger.warn("client.properties exists as directory, cannot load config. Using defaults.");
                saveConfig();
                return;
            }
            
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
                properties.load(fis);
                
                serverUrl = properties.getProperty("server.url", serverUrl);
                localSyncPath = properties.getProperty("sync.path", localSyncPath);
                clientId = properties.getProperty("client.id", clientId);
                username = properties.getProperty("user.username", username);
                token = properties.getProperty("auth.token", token);
                refreshToken = properties.getProperty("auth.refresh_token", refreshToken);
                syncInterval = Integer.parseInt(properties.getProperty("sync.interval", String.valueOf(syncInterval)));
                
                logger.info("Configuration loaded successfully");
            }
        } catch (IOException e) {
            // Config file doesn't exist, use defaults
            logger.info("No configuration file found, using defaults");
            saveConfig();
        }
    }
    
    public void saveConfig() {
        try {
            // Check if client.properties exists as a directory and remove it
            Path configPath = Paths.get(CONFIG_FILE);
            if (Files.isDirectory(configPath)) {
                logger.warn("client.properties exists as directory, removing it to create config file");
                Files.delete(configPath);
            }
            
            properties.setProperty("server.url", serverUrl);
            properties.setProperty("sync.path", localSyncPath);
            properties.setProperty("client.id", clientId);
            if (username != null) properties.setProperty("user.username", username);
            if (token != null) properties.setProperty("auth.token", token);
            if (refreshToken != null) properties.setProperty("auth.refresh_token", refreshToken);
            properties.setProperty("sync.interval", String.valueOf(syncInterval));
            
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                properties.store(fos, "File Sync Client Configuration");
                logger.info("Configuration saved successfully");
            }
        } catch (IOException e) {
            logger.error("Failed to save configuration: {}", e.getMessage());
        }
    }
    
    /**
     * Clear all configuration properties and reset to defaults
     */
    public void clearConfig() {
        try {
            // Clear the properties object
            properties.clear();
            
            // Reset all fields to defaults
            serverUrl = "http://localhost:8080/api";
            localSyncPath = "./sync";
            clientId = generateDefaultClientId();
            username = null;
            token = null;
            refreshToken = null;
            syncInterval = 10;
            
            // Create an empty configuration file
            Path configPath = Paths.get(CONFIG_FILE);
            if (Files.isDirectory(configPath)) {
                logger.warn("client.properties exists as directory, removing it to create config file");
                Files.delete(configPath);
            }
            
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                properties.store(fos, "File Sync Client Configuration - Cleared");
                logger.info("Configuration cleared successfully");
            }
        } catch (IOException e) {
            logger.error("Failed to clear configuration: {}", e.getMessage());
        }
    }
    
    public void initializeSyncDirectory() {
        try {
            Files.createDirectories(Paths.get(localSyncPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create sync directory: " + localSyncPath, e);
        }
    }
    
    /**
     * Generate a default client ID (random UUID) for when no user is logged in
     */
    private String generateDefaultClientId() {
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * Generate a deterministic client ID based on username only
     * This ensures the same user gets the same client ID across all devices
     */
    private String generateUserClientId(String username) {
        if (username == null || username.trim().isEmpty()) {
            return generateDefaultClientId();
        }
        
        try {
            // Create deterministic ID based only on username
            // This ensures same user gets same client ID on any device
            String combined = "filesync_user_" + username.toLowerCase().trim();
            
            // Use SHA-256 to create a consistent hash
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes("UTF-8"));
            
            // Convert to hex string and take first 32 characters for UUID format
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // Format as UUID-like string
            String hashStr = hexString.toString().substring(0, 32);
            return String.format("%s-%s-%s-%s-%s",
                hashStr.substring(0, 8),
                hashStr.substring(8, 12),
                hashStr.substring(12, 16),
                hashStr.substring(16, 20),
                hashStr.substring(20, 32)
            );
            
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            // Fallback to random UUID if any error occurs
            System.err.println("Error generating user-specific client ID: " + e.getMessage());
            return generateDefaultClientId();
        }
    }

    /**
     * Update client ID when user logs in
     * This should be called after successful login
     * The same user will always get the same client ID regardless of device
     */
    public void updateClientIdForUser(String username) {
        String newClientId = generateUserClientId(username);
        if (!newClientId.equals(this.clientId)) {
            this.clientId = newClientId;
            saveConfig();
            System.out.println("Updated client ID for user: " + username + " -> " + newClientId);
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
