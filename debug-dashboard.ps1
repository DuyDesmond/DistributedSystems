# Debug script to view server files and client sync status

Write-Host "File Sync Debug Dashboard" -ForegroundColor Green
Write-Host "========================" -ForegroundColor Green

function Show-ServerFiles {
    Write-Host ""
    Write-Host "📁 Server Storage Files:" -ForegroundColor Yellow
    if (Test-Path ".\server\storage") {
        Get-ChildItem ".\server\storage" -Recurse -File | ForEach-Object {
            $size = [math]::Round($_.Length / 1KB, 2)
            Write-Host "  📄 $($_.FullName.Replace((Get-Location).Path, '.')) ($size KB)" -ForegroundColor Cyan
        }
    } else {
        Write-Host "  No server storage directory found" -ForegroundColor Red
    }
}

function Show-ClientFiles {
    param($clientName, $path)
    Write-Host ""
    Write-Host "📱 $clientName Files:" -ForegroundColor Yellow
    if (Test-Path $path) {
        Get-ChildItem $path -File | ForEach-Object {
            $size = [math]::Round($_.Length / 1KB, 2)
            Write-Host "  📄 $($_.Name) ($size KB)" -ForegroundColor Cyan
        }
    } else {
        Write-Host "  No sync directory found at $path" -ForegroundColor Red
    }
}

function Show-DatabaseInfo {
    Write-Host ""
    Write-Host "🗄️ Database Files:" -ForegroundColor Yellow
    
    if (Test-Path ".\client\file_sync.db") {
        $size = [math]::Round((Get-Item ".\client\file_sync.db").Length / 1KB, 2)
        Write-Host "  📊 Client 1 DB: file_sync.db ($size KB)" -ForegroundColor Cyan
    }
    
    if (Test-Path ".\client2\file_sync_client2.db") {
        $size = [math]::Round((Get-Item ".\client2\file_sync_client2.db").Length / 1KB, 2)
        Write-Host "  📊 Client 2 DB: file_sync_client2.db ($size KB)" -ForegroundColor Cyan
    }
}

function Show-ContainerStatus {
    Write-Host ""
    Write-Host "🐳 Container Status:" -ForegroundColor Yellow
    
    $containers = @("filesync-server", "filesync-client1", "filesync-client2", "filesync-postgres")
    foreach ($container in $containers) {
        $status = docker ps --filter "name=$container" --format "table {{.Status}}" | Select-Object -Skip 1
        if ($status) {
            Write-Host "  ✅ $container`: $status" -ForegroundColor Green
        } else {
            Write-Host "  ❌ $container`: Not running" -ForegroundColor Red
        }
    }
}

function Show-ServerLogs {
    Write-Host ""
    Write-Host "📋 Recent Server Logs:" -ForegroundColor Yellow
    try {
        $logs = docker logs filesync-server --tail 10 2>$null
        if ($logs) {
            $logs | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
        } else {
            Write-Host "  No server logs available" -ForegroundColor Red
        }
    } catch {
        Write-Host "  Cannot retrieve server logs" -ForegroundColor Red
    }
}

function Open-DebugBrowser {
    $serverUrl = "http://localhost:8080/api/files"
    Write-Host ""
    Write-Host "🌐 Opening server API in browser..." -ForegroundColor Yellow
    Write-Host "   URL: $serverUrl" -ForegroundColor Cyan
    Start-Process $serverUrl
}

# Main dashboard
while ($true) {
    Clear-Host
    Write-Host "File Sync Debug Dashboard" -ForegroundColor Green
    Write-Host "========================" -ForegroundColor Green
    Write-Host "$(Get-Date)" -ForegroundColor Gray
    
    Show-ContainerStatus
    Show-ServerFiles
    Show-ClientFiles "Client 1 (Device 1)" ".\client\sync"
    Show-ClientFiles "Client 2 (Device 2)" ".\client2\sync"
    Show-DatabaseInfo
    Show-ServerLogs
    
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Yellow
    Write-Host "  [R] Refresh" -ForegroundColor White
    Write-Host "  [B] Open Browser to Server API" -ForegroundColor White
    Write-Host "  [L] View detailed logs" -ForegroundColor White
    Write-Host "  [Q] Quit" -ForegroundColor White
    
    $choice = Read-Host "Enter choice"
    
    switch ($choice.ToUpper()) {
        "R" { continue }
        "B" { Open-DebugBrowser }
        "L" { 
            Write-Host ""
            Write-Host "Select container for detailed logs:" -ForegroundColor Yellow
            Write-Host "1. Server" -ForegroundColor White
            Write-Host "2. Client 1" -ForegroundColor White  
            Write-Host "3. Client 2" -ForegroundColor White
            $logChoice = Read-Host "Enter choice (1-3)"
            switch ($logChoice) {
                "1" { docker logs filesync-server -f }
                "2" { docker logs filesync-client1 -f }
                "3" { docker logs filesync-client2 -f }
            }
        }
        "Q" { break }
    }
}
