# Reset Client SQLite Databases Script
# This script fully resets the SQLite databases for both client instances

$ErrorActionPreference = "Continue"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Client1DbPath = Join-Path $ProjectRoot "client\file_sync.db"
$Client2DbPath = Join-Path $ProjectRoot "client2\file_sync.db"

Write-Host "=== Client Database Reset Script ===" -ForegroundColor Cyan
Write-Host "Project Root: $ProjectRoot" -ForegroundColor Gray

# Function to safely remove database file
function Remove-DatabaseFile {
    param(
        [string]$DbPath,
        [string]$ClientName
    )
    
    Write-Host "`nProcessing $ClientName database..." -ForegroundColor Yellow
    
    if (-not (Test-Path $DbPath)) {
        Write-Host "  ✓ Database file does not exist: $DbPath" -ForegroundColor Green
        return $true
    }
    
    # Try to remove the file
    try {
        Remove-Item $DbPath -Force
        Write-Host "  ✓ Successfully removed: $DbPath" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "  ✗ Failed to remove database file: $_" -ForegroundColor Red
        return $false
    }
}

# Function to stop Java processes related to the project
function Stop-ProjectJavaProcesses {
    Write-Host "`nStopping Java processes..." -ForegroundColor Yellow
    
    # Get all Java processes
    $javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
    
    if ($javaProcesses) {
        Write-Host "  Found $($javaProcesses.Count) Java process(es)" -ForegroundColor Gray
        Write-Host "  Force stopping all Java processes..." -ForegroundColor Yellow
        Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
        Write-Host "  ✓ Java processes stopped" -ForegroundColor Green
    } else {
        Write-Host "  ✓ No Java processes found" -ForegroundColor Green
    }
}

# Function to test database removal
function Test-DatabaseRemoval {
    Write-Host "`nVerifying database removal..." -ForegroundColor Yellow
    
    $client1Exists = Test-Path $Client1DbPath
    $client2Exists = Test-Path $Client2DbPath
    
    Write-Host "  Client 1 database exists: $client1Exists" -ForegroundColor $(if($client1Exists) {"Red"} else {"Green"})
    Write-Host "  Client 2 database exists: $client2Exists" -ForegroundColor $(if($client2Exists) {"Red"} else {"Green"})
    
    if (-not $client1Exists -and -not $client2Exists) {
        Write-Host "  ✓ All databases successfully removed!" -ForegroundColor Green
        return $true
    } else {
        Write-Host "  ⚠ Some databases still exist" -ForegroundColor Yellow
        return $false
    }
}

# Main execution
Write-Host "`nStarting database reset process..." -ForegroundColor White

# Stop Java processes if requested
Stop-ProjectJavaProcesses

# Remove database files
Remove-DatabaseFile -DbPath $Client1DbPath -ClientName "Client 1" | Out-Null
Remove-DatabaseFile -DbPath $Client2DbPath -ClientName "Client 2" | Out-Null

# Verify removal
$allRemoved = Test-DatabaseRemoval

# Summary
Write-Host "`n=== Reset Summary ===" -ForegroundColor Cyan
if ($allRemoved) {
    Write-Host "✓ Database reset completed successfully!" -ForegroundColor Green
    Write-Host "  Next time you start the clients, fresh databases will be created." -ForegroundColor Gray
} else {
    Write-Host "⚠ Database reset partially completed" -ForegroundColor Yellow
    Write-Host "  Some files may still be in use. Try restarting your computer if processes are stuck." -ForegroundColor Gray
}

Write-Host "`nScript completed." -ForegroundColor White
