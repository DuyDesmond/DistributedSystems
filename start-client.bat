@echo off
cd /d "c:\Users\Admin\Documents\GitHub\DistributedSystems\client"

echo ===============================================
echo Starting File Sync Client...
echo ===============================================
echo.

REM First, ensure dependencies are downloaded
echo Copying dependencies...
mvn dependency:copy-dependencies -q

REM Get the current Java path
for /f "tokens=*" %%a in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr "java.home"') do set JAVA_HOME_LINE=%%a
for /f "tokens=2* delims= " %%a in ("%JAVA_HOME_LINE%") do set JAVA_HOME=%%b

echo Using Java: %JAVA_HOME%
echo.

REM Method 1: Try JavaFX Maven plugin
echo [Method 1] Trying JavaFX Maven plugin...
mvn javafx:run -q

if %ERRORLEVEL% EQU 0 goto :success

echo.
echo [Method 1] Failed. Trying Method 2...

REM Method 2: Try direct Java command with module path
echo [Method 2] Trying direct Java execution with module path...
java --module-path "target\dependency\javafx-controls-21.0.1-win.jar;target\dependency\javafx-fxml-21.0.1-win.jar;target\dependency\javafx-base-21.0.1-win.jar;target\dependency\javafx-graphics-21.0.1-win.jar" --add-modules javafx.controls,javafx.fxml,javafx.base,javafx.graphics --add-opens javafx.fxml/javafx.fxml=ALL-UNNAMED -cp "target\classes;target\dependency\*" com.filesync.client.FileSyncClientApplication

if %ERRORLEVEL% EQU 0 goto :success

echo.
echo [Method 2] Failed. Trying Method 3...

REM Method 3: Try exec plugin
echo [Method 3] Trying Maven exec plugin...
mvn exec:java -q

if %ERRORLEVEL% EQU 0 goto :success

echo.
echo ===============================================
echo ERROR: All startup methods failed!
echo ===============================================
echo.
echo Possible issues:
echo 1. JavaFX runtime not properly installed
echo 2. Java version incompatibility
echo 3. Missing dependencies
echo.
echo Try installing JavaFX separately:
echo https://openjfx.io/
echo.
echo Or try running with your IDE (IntelliJ IDEA, Eclipse, VS Code)
goto :end

:success
echo.
echo ===============================================
echo Client started successfully!
echo ===============================================

:end
echo.
echo Press any key to exit...
pause >nul
