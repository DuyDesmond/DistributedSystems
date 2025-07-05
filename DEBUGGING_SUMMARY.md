# Distributed File Synchronization System - Bug Analysis & Fixes

## **Issues Found and Resolved**

### 1. **Authentication Issue (RESOLVED)**
- **Problem**: Files not uploading due to unauthenticated state
- **Root Cause**: FileWatchService attempted uploads before user login
- **Fix**: Added authentication checks in FileWatchService and improved error messages

### 2. **WebSocket STOMP Protocol Error (RESOLVED)**
- **Problem**: Server logs showed "SUBSCRIBE shouldn't have a payload" errors
- **Root Cause**: 
  - Missing STOMP CONNECT frame before subscribing
  - Incorrect null terminator in SUBSCRIBE messages
- **Fixes Applied**:
  ```java
  // Added proper STOMP CONNECT frame
  private void sendStompConnect() {
      String connectFrame = "CONNECT\n" +
                           "accept-version:1.0,1.1,1.2\n" +
                           "host:" + getURI().getHost() + "\n";
      if (config.getToken() != null && !config.getToken().isEmpty()) {
          connectFrame += "Authorization:Bearer " + config.getToken() + "\n";
      }
      connectFrame += "\n";
      send(connectFrame);
  }
  
  // Fixed SUBSCRIBE message format
  private String createSubscriptionMessage(String destination) {
      return "SUBSCRIBE\n" +
             "id:sub-" + System.currentTimeMillis() + "\n" +
             "destination:" + destination + "\n" +
             "\n"; // Removed null terminator
  }
  ```

### 3. **File Storage Visibility Issue (DIAGNOSED)**
- **Problem**: Files not visible in expected storage location
- **Root Cause**: Server uses hierarchical storage structure
- **Storage Pattern**: `/app/storage/{userId}/{year}/{month}/{fileId}`
- **Expected Location**: `./server/storage/{userId}/2025/07/{fileId}`

### 4. **Silent Failure Pattern (RESOLVED)**
- **Problem**: Upload failures were logged as warnings, making debugging difficult
- **Fix**: Changed to error-level logging with clear instructions:
  ```java
  logger.error("Cannot upload file - user not authenticated. Please login first: {}", filePath);
  ```

## **Code Changes Summary**

### Files Modified:
1. **`client2/src/main/java/com/filesync/client/service/FileWatchService.java`**
   - Added authentication checks before file scanning and change detection
   - Improved error messages for unauthenticated operations

2. **`client2/src/main/java/com/filesync/client/service/EnhancedSyncService.java`**
   - Enhanced error messages for authentication failures
   - Added startup authentication check for already-logged-in users
   - Improved upload queue processing

3. **`client2/src/main/java/com/filesync/client/service/WebSocketSyncClient.java`**
   - Added proper STOMP protocol implementation
   - Fixed CONNECT frame transmission
   - Corrected SUBSCRIBE message format

## **Testing Steps to Verify Fixes**

1. **Start the server**: `docker-compose up -d`
2. **Run client2 application**
3. **Login with credentials** (username: abc, password: [user's password])
4. **Add test file to sync directory**: `client2/sync/test-file.txt`
5. **Check server storage**: Look for files in `./server/storage/{userId}/2025/07/`
6. **Verify WebSocket connection**: No STOMP errors in server logs

## **Expected File Locations**

After successful upload, files will appear in:
```
./server/storage/
└── {userId}/
    └── 2025/
        └── 07/
            └── {fileId}
```

Where `{userId}` is the database user ID for user "abc" and `{fileId}` is a UUID.

## **Debugging Commands**

### Check server storage:
```bash
# Check for any files in storage
find ./server/storage -type f

# Check Docker container storage
docker exec filesync-server find /app/storage -type f
```

### Check server logs:
```bash
# Recent logs
docker logs filesync-server --tail 50

# Follow logs in real-time
docker logs -f filesync-server
```

### Check database records:
```bash
# Connect to database
docker exec -it filesync-postgres psql -U filesync -d filesync

# Check users and files
SELECT username, user_id FROM users;
SELECT file_path, storage_path FROM files;
```

## **Key Takeaways**

1. **Authentication Required First**: File synchronization only works after user login
2. **WebSocket Protocol Compliance**: STOMP requires proper handshake sequence
3. **Hierarchical Storage**: Files stored in dated directory structure, not flat
4. **Error Visibility**: Clear error messages crucial for debugging distributed systems

The system should now properly:
- Authenticate users before allowing file operations
- Establish proper WebSocket/STOMP connections
- Upload files to the correct storage structure
- Provide clear error messages for troubleshooting