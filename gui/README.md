# Desktop GUI for Distributed File Sync

This directory contains the desktop GUI application for the Distributed File Sync system, built using Python's Tkinter library for cross-platform compatibility.

## Features

### Main Interface
- **Tabbed Interface**: Organized into Files, Sync Events, and Settings tabs
- **Real-time Updates**: Automatic refresh of file status and sync events
- **Cross-platform**: Works on Windows, Linux, and macOS

### Files Tab
- **File Browser**: Visual representation of synchronized files
- **Status Indicators**: Shows sync status for each file (Synced, Pending, Error, etc.)
- **File Information**: Displays file size, modification time, and hash
- **Search/Filter**: Quick filtering of files by name or path
- **File Management**: Add/remove files from sync

### Sync Events Tab
- **Event Monitoring**: Real-time display of sync operations
- **Event History**: Chronological list of all sync activities
- **Status Tracking**: Shows success/failure status of each operation
- **Auto-refresh**: Configurable automatic updates

### Settings Tab
- **Server Configuration**: Set server URL and connection details
- **Authentication**: Username and password management
- **Sync Folder**: Choose local folder for synchronization
- **Sync Interval**: Configure automatic sync frequency
- **Connection Status**: Real-time server connection monitoring

## Usage

### Starting the GUI
```bash
# Using the launcher (recommended)
python launcher.py gui

# Direct execution
python gui/sync_gui.py

# From CLI
python cli.py gui
```

### Configuration
1. **Set Server URL**: Enter your server address (default: http://localhost:8000)
2. **Login**: Provide username and password
3. **Choose Sync Folder**: Select local directory to synchronize
4. **Configure Settings**: Set sync interval and other preferences
5. **Start Sync**: Begin file synchronization

### Menu Options
- **File Menu**:
  - Set Sync Folder: Choose synchronization directory
  - Connect to Server: Establish server connection
  - Exit: Close application

- **Sync Menu**:
  - Start Sync: Begin automatic synchronization
  - Stop Sync: Halt synchronization process
  - Manual Sync: Perform one-time sync
  - Clear Local Cache: Reset sync state

- **View Menu**:
  - Refresh: Update all displays
  - Settings: Go to settings tab

## Technical Details

### Architecture
- **Main Window**: `SyncStatusGUI` class manages the entire interface
- **Threading**: Background sync operations don't block the UI
- **Data Integration**: Connects to `SyncClient` for actual sync operations
- **State Management**: Maintains connection and sync state

### Dependencies
- **tkinter**: Built into Python (no additional installation needed)
- **threading**: For background operations
- **json**: For settings persistence
- **sqlite3**: For local data storage

### Platform Notes
- **Windows**: Tkinter included with Python installation
- **Linux**: May require `sudo apt-get install python3-tk` on some distributions
- **macOS**: Tkinter included with Python installation

## Customization

### Settings File
Settings are automatically saved to `gui_settings.json`:
```json
{
  "server_url": "http://localhost:8000",
  "username": "user",
  "sync_folder": "/path/to/sync",
  "sync_interval": 10
}
```

### Extending the GUI
To add new features:
1. Add new tabs to the notebook widget
2. Implement corresponding methods in `SyncStatusGUI`
3. Update the menu system as needed
4. Maintain threading for non-blocking operations

## Troubleshooting

### Common Issues
1. **Tkinter not found**: Install python3-tk package
2. **Connection errors**: Check server URL and network connectivity
3. **Sync not starting**: Verify login credentials and folder permissions
4. **GUI freezing**: Check for blocking operations in main thread

### Debug Mode
Enable debug logging by modifying the `log_status` method to write to a file for persistent debugging.

## Future Enhancements
- Dark theme support
- File conflict resolution interface
- Bandwidth monitoring
- Advanced filtering options
- System tray integration
- Notification system
