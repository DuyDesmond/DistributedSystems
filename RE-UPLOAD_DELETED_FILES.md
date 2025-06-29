# Re-uploading Previously Deleted Files

## Problem

When a file is deleted from the sync folder, it gets marked as "DELETED" in the local database to prevent race conditions where the file might be re-downloaded from other clients. However, this prevents re-uploading a file with the same name/path later.

## Solution

The system has been enhanced to automatically handle re-uploading of previously deleted files:

### Automatic Handling

1. **When you copy a file back to the sync folder** with the same name/path as a previously deleted file:
   - The system automatically detects that the file was previously marked as "DELETED"
   - It clears the deletion status and proceeds with the upload
   - The file will be synced normally to the server and other clients

### Manual Methods (Advanced)

If you need more control, you can use these methods programmatically:

#### Method 1: Clear Deletion Status and Upload
```java
enhancedSyncService.clearDeletionAndUpload("relative/path/to/file.txt");
```
This method:
- Clears the "DELETED" status for the specified file
- Queues the file for immediate upload
- Only works if the file actually exists in the sync folder

#### Method 2: Clear Deletion Status Only
```java
enhancedSyncService.clearDeletionStatus("relative/path/to/file.txt");
```
This method:
- Only clears the "DELETED" status
- Allows the file to be synced normally when it appears
- Doesn't require the file to exist immediately

## How It Works

### Before the Fix
1. File `document.txt` is deleted → marked as "DELETED" in database
2. User copies a new `document.txt` to sync folder
3. System refuses to upload: "Skipping upload of file marked as deleted"
4. File never gets synced ❌

### After the Fix
1. File `document.txt` is deleted → marked as "DELETED" in database  
2. User copies a new `document.txt` to sync folder
3. System detects: "File was previously deleted but now exists again"
4. System clears "DELETED" status and uploads the file
5. File gets synced to server and other clients ✅

## Example Scenario

1. **Delete a file**: Delete `my-document.pdf` from `client/sync/` folder
2. **Wait for sync**: The file disappears from `client2/sync/` folder
3. **Re-add the file**: Copy a new file named `my-document.pdf` to `client/sync/`
4. **Automatic handling**: System automatically uploads the new file
5. **Result**: The new file appears in `client2/sync/` folder

## Database States

The file can be in these sync states:
- `PENDING` - Waiting to be synced
- `SYNCED` - Successfully synced with server
- `DELETED` - Marked as deleted (prevents re-download)

When you re-upload a deleted file:
- `DELETED` → `PENDING` → `SYNCED`

## Error Handling

If the server deletion request fails but the local file is deleted:
- The file stays marked as "DELETED" locally
- This prevents the file from being re-downloaded from other clients
- When you add the file back, it will still be uploaded normally

## Best Practices

1. **Normal workflow**: Just copy files in/out of the sync folder - the system handles everything automatically
2. **Programmatic control**: Use the manual methods only if you need fine-grained control
3. **Monitoring**: Check the logs to see when deletion status is cleared:
   ```
   INFO: File was previously deleted but now exists again. Clearing deletion status and uploading: my-document.pdf
   ```

This enhancement ensures that users can freely delete and re-add files without getting stuck in "deletion limbo" while maintaining the race condition protection for multi-client scenarios.
