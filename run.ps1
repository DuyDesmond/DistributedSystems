# Distributed File Sync System - Windows Setup Script
# Run this script in PowerShell to set up and start the desktop application

param(
    [switch]$Docker,
    [switch]$Dev,
    [switch]$GUI,
    [switch]$CLI,
    [switch]$Sync,
    [switch]$Help
)

function Show-Help {
    Write-Host "Distributed File Sync System - Windows Setup Script" -ForegroundColor Green
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\run.ps1                  # Start server with Docker and launch GUI"
    Write-Host "  .\run.ps1 -Docker          # Start server services only with Docker"
    Write-Host "  .\run.ps1 -GUI             # Launch desktop GUI interface"
    Write-Host "  .\run.ps1 -CLI             # Launch command-line interface"
    Write-Host "  .\run.ps1 -Sync            # Start sync client only"
    Write-Host "  .\run.ps1 -Dev             # Development mode with hot reload"
    Write-Host "  .\run.ps1 -Help            # Show this help"
    Write-Host ""
    Write-Host "Desktop Application Interfaces:"
    Write-Host "  GUI  - Graphical user interface with visual monitoring"
    Write-Host "  CLI  - Command-line interface for automation and advanced users"
    Write-Host "  Sync - Background sync service (headless)"
    Write-Host ""
}

function Test-Command {
    param($Command)
    try {
        Get-Command $Command -ErrorAction Stop | Out-Null
        return $true
    }
    catch {
        return $false
    }
}

function Install-Dependencies {
    Write-Host "Installing Python dependencies..." -ForegroundColor Yellow
    
    if (Test-Path "requirements.txt") {
        pip install -r requirements.txt
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Dependencies installed successfully" -ForegroundColor Green
        } else {
            Write-Host "✗ Failed to install dependencies" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "✗ requirements.txt not found" -ForegroundColor Red
        exit 1
    }
}

function Start-DockerServices {
    Write-Host "Starting services with Docker Compose..." -ForegroundColor Yellow
    
    if (-not (Test-Command "docker-compose")) {
        Write-Host "✗ Docker Compose not found. Please install Docker Desktop." -ForegroundColor Red
        exit 1
    }
    
    if (Test-Path "docker-compose.yml") {
        docker-compose up -d
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Docker services started successfully" -ForegroundColor Green
            Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
            Start-Sleep -Seconds 15
        } else {
            Write-Host "✗ Failed to start Docker services" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "✗ docker-compose.yml not found" -ForegroundColor Red
        exit 1
    }
}

function Create-Directories {
    $directories = @(
        "storage",
        "storage\content",
        "storage\chunks", 
        "storage\temp",
        "storage\metadata",
        "logs"
    )
    
    foreach ($dir in $directories) {
        if (-not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
            Write-Host "✓ Created directory: $dir" -ForegroundColor Green
        }
    }
}

function Start-Server {
    Write-Host "Starting File Sync Server..." -ForegroundColor Yellow
    
    $env:PYTHONPATH = Get-Location
    
    if ($Dev) {
        # Development mode with auto-reload
        Start-Process -NoNewWindow -FilePath "python" -ArgumentList "-m", "uvicorn", "server.main:app", "--reload", "--host", "0.0.0.0", "--port", "8000"
    } else {
        Start-Process -NoNewWindow -FilePath "python" -ArgumentList "server\main.py"
    }
    
    Write-Host "✓ Server starting..." -ForegroundColor Green
    Write-Host "Server will be available at: http://localhost:8000" -ForegroundColor Cyan
}

function Start-DesktopGUI {
    Write-Host "Starting Desktop GUI..." -ForegroundColor Yellow
    python launcher.py gui
}

function Start-CLI {
    Write-Host "Starting Command-Line Interface..." -ForegroundColor Yellow
    python launcher.py cli
}

function Start-SyncClient {
    Write-Host "Starting Sync Client..." -ForegroundColor Yellow
    python launcher.py sync
}

function Show-Instructions {
    Write-Host "Desktop File Sync System is ready!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Available Interfaces:" -ForegroundColor White
    Write-Host "1. Desktop GUI - Visual monitoring and control" -ForegroundColor White
    Write-Host "   python launcher.py gui" -ForegroundColor Gray
    Write-Host ""
    Write-Host "2. Command Line Interface - Full CLI control" -ForegroundColor White
    Write-Host "   python launcher.py cli" -ForegroundColor Gray
    Write-Host ""
    Write-Host "3. Background Sync Client - Headless operation" -ForegroundColor White
    Write-Host "   python launcher.py sync" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Server API documentation:" -ForegroundColor White
    Write-Host "   http://localhost:8000/docs" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Press Ctrl+C to stop the server" -ForegroundColor Yellow
}

function Main {
    if ($Help) {
        Show-Help
        return
    }
    
    Write-Host "Distributed File Sync System - Windows Setup" -ForegroundColor Green
    Write-Host "=" * 50 -ForegroundColor Green
    
    # Check Python
    if (-not (Test-Command "python")) {
        Write-Host "✗ Python not found. Please install Python 3.11 or later." -ForegroundColor Red
        exit 1
    }
    
    $pythonVersion = python --version 2>&1
    Write-Host "✓ Found $pythonVersion" -ForegroundColor Green
    
    # Check pip
    if (-not (Test-Command "pip")) {
        Write-Host "✗ pip not found. Please install pip." -ForegroundColor Red
        exit 1
    }
    
    # Handle interface-specific launches
    if ($GUI) {
        Install-Dependencies
        Start-DesktopGUI
        return
    }
    
    if ($CLI) {
        Install-Dependencies
        Start-CLI
        return
    }
    
    if ($Sync) {
        Install-Dependencies
        Start-SyncClient
        return
    }
    
    # Create directories
    Write-Host "Creating directories..." -ForegroundColor Yellow
    Create-Directories
    
    # Install dependencies
    Install-Dependencies
    
    # Start services
    if ($Docker) {
        Start-DockerServices
    } else {
        Write-Host "Note: Starting server requires Docker. Use -Docker flag or install services manually." -ForegroundColor Yellow
        Write-Host "For desktop client only, use: .\run.ps1 -GUI" -ForegroundColor Yellow
        return
    }
    
    # Start server
    Start-Server
    
    # Wait a moment for server to start
    Start-Sleep -Seconds 3
    
    # Show instructions
    Show-Instructions
    
    # Prompt to launch GUI
    Write-Host ""
    $launchGUI = Read-Host "Would you like to launch the Desktop GUI now? (y/n)"
    if ($launchGUI -eq 'y' -or $launchGUI -eq 'Y') {
        Start-Process powershell -ArgumentList "-Command", "cd '$PWD'; python launcher.py gui"
    }
    
    # Keep script running
    try {
        while ($true) {
            Start-Sleep -Seconds 1
        }
    }
    catch {
        Write-Host ""
        Write-Host "Shutting down..." -ForegroundColor Yellow
        
        if ($Docker) {
            Write-Host "Stopping Docker services..." -ForegroundColor Yellow
            docker-compose down
        }
        
        Write-Host "✓ Shutdown complete" -ForegroundColor Green
    }
}

# Run main function
Main
