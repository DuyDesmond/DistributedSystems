@echo off
REM Desktop File Sync Application Launcher (Windows Batch)
REM Quick launcher for the desktop file sync application

echo.
echo =====================================================
echo   Distributed File Sync - Desktop Application
echo =====================================================
echo.

set choice=
echo Choose an interface:
echo.
echo [1] Desktop GUI    - Graphical user interface
echo [2] Command Line   - CLI interface  
echo [3] Background     - Sync client only
echo [4] Help          - Show help information
echo.
set /p choice="Enter your choice (1-4): "

if "%choice%"=="1" (
    echo Starting Desktop GUI...
    python launcher.py gui
) else if "%choice%"=="2" (
    echo Starting Command Line Interface...
    python launcher.py cli
) else if "%choice%"=="3" (
    echo Starting Background Sync Client...
    python launcher.py sync
) else if "%choice%"=="4" (
    echo.
    echo Desktop File Sync Application
    echo.
    echo Available interfaces:
    echo   python launcher.py gui   - Desktop GUI with visual monitoring
    echo   python launcher.py cli   - Command-line interface
    echo   python launcher.py sync  - Background sync client
    echo.
    echo For full setup: .\run.ps1 -GUI
    echo.
    pause
) else (
    echo Invalid choice. Please run the script again.
    pause
)
