@echo off
setlocal enabledelayedexpansion

:: Reset Client SQLite Databases Script
:: This script fully resets the SQLite databases for both client instances

title Reset Client SQLite Databases

echo ========================================
echo    Client Database Reset Script
echo ========================================
echo.

:: Get the directory where this script is located
set "PROJECT_ROOT=%~dp0"
set "CLIENT1_DB=%PROJECT_ROOT%client\file_sync.db"
set "CLIENT2_DB=%PROJECT_ROOT%client2\file_sync.db"

echo Project Root: %PROJECT_ROOT%
echo.

:: Function to stop Java processes
echo [1/3] Stopping Java processes...
echo.

:: Check for running Java processes
tasklist /FI "IMAGENAME eq java.exe" /NH 2>nul | find /I "java.exe" >nul
if %ERRORLEVEL% == 0 (
    echo   Found Java processes running
    echo   Force stopping all Java processes...
    taskkill /F /IM java.exe /T >nul 2>&1
    timeout /t 2 /nobreak >nul
    echo   ^✓ Java processes stopped
) else (
    echo   ^✓ No Java processes found
)
echo.

:: Function to remove database files
echo [2/3] Removing database files...
echo.

:: Remove Client 1 database
echo   Processing Client 1 database...
if exist "%CLIENT1_DB%" (
    del /F /Q "%CLIENT1_DB%" >nul 2>&1
    if exist "%CLIENT1_DB%" (
        echo   ^✗ Failed to remove: %CLIENT1_DB%
        set "CLIENT1_SUCCESS=false"
    ) else (
        echo   ^✓ Successfully removed: %CLIENT1_DB%
        set "CLIENT1_SUCCESS=true"
    )
) else (
    echo   ^✓ Database file does not exist: %CLIENT1_DB%
    set "CLIENT1_SUCCESS=true"
)

:: Remove Client 2 database
echo   Processing Client 2 database...
if exist "%CLIENT2_DB%" (
    del /F /Q "%CLIENT2_DB%" >nul 2>&1
    if exist "%CLIENT2_DB%" (
        echo   ^✗ Failed to remove: %CLIENT2_DB%
        set "CLIENT2_SUCCESS=false"
    ) else (
        echo   ^✓ Successfully removed: %CLIENT2_DB%
        set "CLIENT2_SUCCESS=true"
    )
) else (
    echo   ^✓ Database file does not exist: %CLIENT2_DB%
    set "CLIENT2_SUCCESS=true"
)
echo.

:: Verify removal
echo [3/3] Verifying database removal...
echo.

set "ALL_REMOVED=true"

if exist "%CLIENT1_DB%" (
    echo   Client 1 database exists: TRUE
    set "ALL_REMOVED=false"
) else (
    echo   Client 1 database exists: FALSE
)

if exist "%CLIENT2_DB%" (
    echo   Client 2 database exists: TRUE
    set "ALL_REMOVED=false"
) else (
    echo   Client 2 database exists: FALSE
)
echo.

:: Summary
echo ========================================
echo              Reset Summary
echo ========================================

if "!ALL_REMOVED!" == "true" (
    echo ^✓ Database reset completed successfully!
    echo   Next time you start the clients, fresh databases will be created.
    echo.
    echo SUCCESS: All databases have been reset.
) else (
    echo ^⚠ Database reset partially completed
    echo   Some files may still be in use.
    echo   Try restarting your computer if processes are stuck.
    echo.
    echo WARNING: Some databases could not be removed.
)

echo.
echo Script completed.
echo.
echo Press any key to exit...
pause >nul
