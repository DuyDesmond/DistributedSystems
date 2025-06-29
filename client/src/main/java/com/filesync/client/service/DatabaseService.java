package com.filesync.client.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filesync.common.model.VersionVector;

/**
 * Database service for client-side SQLite operations
 * Manages local file version vectors and sync state
 */
public class DatabaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    
    private final String databasePath;
    private Connection connection;
    
    public DatabaseService(String databasePath) {
        this.databasePath = databasePath;
        initializeDatabase();
    }
    
    /**
     * Initialize SQLite database and create tables
     */
    private void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            
            createTables();
            logger.info("SQLite database initialized at: {}", databasePath);
            
        } catch (ClassNotFoundException | SQLException e) {
            logger.error("Failed to initialize SQLite database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Create necessary tables
     */
    private void createTables() throws SQLException {
        String createFileVersionVectorTable = """
            CREATE TABLE IF NOT EXISTS file_version_vector (
                file_id TEXT PRIMARY KEY,
                file_path TEXT NOT NULL,
                version_vector TEXT NOT NULL,
                last_modified TIMESTAMP NOT NULL,
                file_size INTEGER,
                checksum TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        String createSyncQueueTable = """
            CREATE TABLE IF NOT EXISTS sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL,
                operation TEXT NOT NULL,
                priority INTEGER DEFAULT 5,
                retry_count INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                scheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                error_message TEXT
            )
            """;
        
        String createClientConfigTable = """
            CREATE TABLE IF NOT EXISTS client_config (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createFileVersionVectorTable);
            stmt.executeUpdate(createSyncQueueTable);
            stmt.executeUpdate(createClientConfigTable);
            
            // Create indexes for better performance
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_file_path ON file_version_vector(file_path)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sync_status ON file_version_vector(sync_status)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sync_queue_operation ON sync_queue(operation)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sync_queue_priority ON sync_queue(priority, scheduled_at)");
        }
    }
    
    /**
     * Store or update file version vector
     */
    public void storeFileVersionVector(String fileId, String filePath, VersionVector versionVector, 
                                     LocalDateTime lastModified, Long fileSize, String checksum) {
        String sql = """
            INSERT OR REPLACE INTO file_version_vector 
            (file_id, file_path, version_vector, last_modified, file_size, checksum, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            pstmt.setString(2, filePath);
            pstmt.setString(3, versionVector.toJson());
            pstmt.setTimestamp(4, Timestamp.valueOf(lastModified));
            pstmt.setObject(5, fileSize);
            pstmt.setString(6, checksum);
            
            pstmt.executeUpdate();
            logger.debug("Stored version vector for file: {}", filePath);
            
        } catch (SQLException e) {
            logger.error("Failed to store file version vector for: " + filePath, e);
            throw new RuntimeException("Database operation failed", e);
        }
    }
    
    /**
     * Get file version vector
     */
    public VersionVector getFileVersionVector(String fileId) {
        String sql = "SELECT version_vector FROM file_version_vector WHERE file_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String vectorJson = rs.getString("version_vector");
                    return VersionVector.fromJson(vectorJson);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get file version vector for ID: " + fileId, e);
        }
        
        return new VersionVector(); // Return empty vector if not found
    }
    
    /**
     * Get file version vector by path
     */
    public VersionVector getFileVersionVectorByPath(String filePath) {
        String sql = "SELECT version_vector FROM file_version_vector WHERE file_path = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String vectorJson = rs.getString("version_vector");
                    return VersionVector.fromJson(vectorJson);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get file version vector for path: " + filePath, e);
        }
        
        return new VersionVector(); // Return empty vector if not found
    }
    
    /**
     * Get all files that need syncing
     */
    public Map<String, VersionVector> getPendingSyncFiles() {
        Map<String, VersionVector> pendingFiles = new HashMap<>();
        String sql = "SELECT file_path, version_vector FROM file_version_vector WHERE sync_status = 'PENDING'";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                String filePath = rs.getString("file_path");
                String vectorJson = rs.getString("version_vector");
                pendingFiles.put(filePath, VersionVector.fromJson(vectorJson));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get pending sync files", e);
        }
        
        return pendingFiles;
    }
    
    /**
     * Update sync status for a file
     */
    public void updateSyncStatus(String filePath, String status) {
        String sql = "UPDATE file_version_vector SET sync_status = ? WHERE file_path = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, filePath);
            
            int updated = pstmt.executeUpdate();
            if (updated > 0) {
                logger.debug("Updated sync status for {}: {}", filePath, status);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to update sync status for: " + filePath, e);
        }
    }
    
    /**
     * Get sync status for a file
     */
    public String getSyncStatus(String filePath) {
        String sql = "SELECT sync_status FROM file_version_vector WHERE file_path = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("sync_status");
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get sync status for: " + filePath, e);
        }
        
        return null;
    }
    
    /**
     * Add item to sync queue
     */
    public void addToSyncQueue(String filePath, String operation, int priority) {
        String sql = """
            INSERT INTO sync_queue (file_path, operation, priority, scheduled_at)
            VALUES (?, ?, ?, ?)
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            pstmt.setString(2, operation);
            pstmt.setInt(3, priority);
            pstmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            
            pstmt.executeUpdate();
            logger.debug("Added to sync queue: {} ({})", filePath, operation);
            
        } catch (SQLException e) {
            logger.error("Failed to add to sync queue: " + filePath, e);
        }
    }
    
    /**
     * Get next item from sync queue
     */
    public SyncQueueItem getNextSyncItem() {
        String sql = """
            SELECT id, file_path, operation, priority, retry_count 
            FROM sync_queue 
            WHERE scheduled_at <= ? 
            ORDER BY priority ASC, created_at ASC 
            LIMIT 1
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new SyncQueueItem(
                        rs.getLong("id"),
                        rs.getString("file_path"),
                        rs.getString("operation"),
                        rs.getInt("priority"),
                        rs.getInt("retry_count")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get next sync item", e);
        }
        
        return null;
    }
    
    /**
     * Remove item from sync queue
     */
    public void removeSyncQueueItem(long id) {
        String sql = "DELETE FROM sync_queue WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to remove sync queue item: " + id, e);
        }
    }
    
    /**
     * Store configuration value
     */
    public void setConfig(String key, String value) {
        String sql = "INSERT OR REPLACE INTO client_config (key, value, updated_at) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to store config: " + key, e);
        }
    }
    
    /**
     * Get configuration value
     */
    public String getConfig(String key, String defaultValue) {
        String sql = "SELECT value FROM client_config WHERE key = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get config: " + key, e);
        }
        
        return defaultValue;
    }
    
    /**
     * Get all tracked files with their sync status
     */
    public Map<String, String> getAllTrackedFiles() {
        Map<String, String> trackedFiles = new HashMap<>();
        String sql = "SELECT file_path, sync_status FROM file_version_vector";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                String filePath = rs.getString("file_path");
                String syncStatus = rs.getString("sync_status");
                trackedFiles.put(filePath, syncStatus);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get all tracked files", e);
        }
        
        return trackedFiles;
    }

    /**
     * Remove file record from database
     */
    public void removeFileRecord(String filePath) {
        String sql1 = "DELETE FROM file_version_vector WHERE file_path = ?";
        String sql2 = "DELETE FROM sync_queue WHERE file_path = ?";
        
        try (PreparedStatement pstmt1 = connection.prepareStatement(sql1);
             PreparedStatement pstmt2 = connection.prepareStatement(sql2)) {
            
            // Remove from file_version_vector table
            pstmt1.setString(1, filePath);
            int deleted1 = pstmt1.executeUpdate();
            
            // Remove from sync_queue table
            pstmt2.setString(1, filePath);
            int deleted2 = pstmt2.executeUpdate();
            
            logger.debug("Removed file record: {} (version_vector: {}, sync_queue: {})", 
                        filePath, deleted1, deleted2);
            
        } catch (SQLException e) {
            logger.error("Failed to remove file record: " + filePath, e);
        }
    }
    
    /**
     * Close database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Failed to close database connection", e);
        }
    }
    
    /**
     * Sync queue item representation
     */
    public static class SyncQueueItem {
        private final long id;
        private final String filePath;
        private final String operation;
        private final int priority;
        private final int retryCount;
        
        public SyncQueueItem(long id, String filePath, String operation, int priority, int retryCount) {
            this.id = id;
            this.filePath = filePath;
            this.operation = operation;
            this.priority = priority;
            this.retryCount = retryCount;
        }
        
        // Getters
        public long getId() { return id; }
        public String getFilePath() { return filePath; }
        public String getOperation() { return operation; }
        public int getPriority() { return priority; }
        public int getRetryCount() { return retryCount; }
    }
}
