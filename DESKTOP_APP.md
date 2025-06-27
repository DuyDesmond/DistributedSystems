# Desktop File Sync Application

This is a **DESKTOP APPLICATION** - not a web application.

## Primary Interfaces

### 1. Desktop GUI (Recommended)
- **Native desktop application** built with Tkinter
- Visual file monitoring and sync status
- Real-time sync event tracking
- Easy configuration and settings management
- Cross-platform (Windows, Linux, macOS)

**Launch:** `python launcher.py gui`

### 2. Command Line Interface (CLI)
- Full-featured command-line interface
- Perfect for automation and power users
- Complete file and user management
- Sync control and monitoring

**Launch:** `python launcher.py cli` or `python cli.py`

### 3. Background Sync Client
- Headless operation for servers
- Continuous file monitoring
- Lightweight background process

**Launch:** `python launcher.py sync`

## Windows Users
Use the PowerShell script for easy setup:
```powershell
.\run.ps1 -GUI    # Launch desktop GUI
.\run.ps1 -CLI    # Launch command-line interface
.\run.ps1 -Sync   # Start background sync client
```

## Not a Web App
This application does **NOT** require a web browser. All interfaces are native desktop applications or command-line tools. The server component provides a REST API for the desktop clients to communicate with, but the user interfaces are desktop-based.
