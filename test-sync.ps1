# Test script for simulating file synchronization between two devices

Write-Host "Testing File Synchronization Between Two Devices" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green

# Check if containers are running
$client1Status = docker ps --filter "name=filesync-client1" --format "table {{.Status}}" | Select-Object -Skip 1
$client2Status = docker ps --filter "name=filesync-client2" --format "table {{.Status}}" | Select-Object -Skip 1

if (-not $client1Status -or -not $client2Status) {
    Write-Host "Error: Clients are not running. Please run start-two-clients.ps1 first" -ForegroundColor Red
    exit 1
}

Write-Host "Both clients are running!" -ForegroundColor Green
Write-Host "Client 1 Status: $client1Status" -ForegroundColor Cyan
Write-Host "Client 2 Status: $client2Status" -ForegroundColor Cyan

Write-Host ""
Write-Host "Creating test files to simulate device sync..." -ForegroundColor Yellow

# Create test files in client1 sync directory
$testFile1 = ".\client\sync\device1-test-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"
$testContent1 = "This file was created on Device 1 at $(Get-Date)"
Set-Content -Path $testFile1 -Value $testContent1
Write-Host "Created test file on Device 1: $testFile1" -ForegroundColor Green

# Wait a moment for sync
Start-Sleep 5

# Create test files in client2 sync directory  
$testFile2 = ".\client2\sync\device2-test-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"
$testContent2 = "This file was created on Device 2 at $(Get-Date)"
Set-Content -Path $testFile2 -Value $testContent2
Write-Host "Created test file on Device 2: $testFile2" -ForegroundColor Green

Write-Host ""
Write-Host "Waiting 10 seconds for synchronization..." -ForegroundColor Yellow
Start-Sleep 10

Write-Host ""
Write-Host "Checking synchronization results:" -ForegroundColor Yellow

# Check if device1 file appears in client2
$device1FileName = Split-Path $testFile1 -Leaf
$syncedFile1 = ".\client2\sync\$device1FileName"
if (Test-Path $syncedFile1) {
    Write-Host "✅ SUCCESS: Device 1 file synced to Device 2" -ForegroundColor Green
    Write-Host "   File: $syncedFile1" -ForegroundColor Cyan
} else {
    Write-Host "❌ FAILED: Device 1 file NOT synced to Device 2" -ForegroundColor Red
}

# Check if device2 file appears in client1
$device2FileName = Split-Path $testFile2 -Leaf
$syncedFile2 = ".\client\sync\$device2FileName"
if (Test-Path $syncedFile2) {
    Write-Host "✅ SUCCESS: Device 2 file synced to Device 1" -ForegroundColor Green
    Write-Host "   File: $syncedFile2" -ForegroundColor Cyan
} else {
    Write-Host "❌ FAILED: Device 2 file NOT synced to Device 1" -ForegroundColor Red
}

Write-Host ""
Write-Host "Current files in sync directories:" -ForegroundColor Yellow
Write-Host "Device 1 (.\client\sync\):" -ForegroundColor Cyan
Get-ChildItem ".\client\sync\" -File | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor White }

Write-Host "Device 2 (.\client2\sync\):" -ForegroundColor Cyan  
Get-ChildItem ".\client2\sync\" -File | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor White }

Write-Host ""
Write-Host "You can also manually add/edit/delete files in either directory to test sync" -ForegroundColor Yellow
