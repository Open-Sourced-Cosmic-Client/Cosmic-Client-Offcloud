@echo off
title Cosmic Client Offcloud - Launcher
cd /d "%~dp0"

:: Sanitize environment variables against rogue agent injection
set "_JAVA_OPTIONS="
set "JAVA_TOOL_OPTIONS="
set "JAVA_OPTIONS="
set "IBM_JAVA_OPTIONS="

:: 0. Extraction Check
if not exist "%~dp0CosmicClient-x64\1.8\CosmicClient-1.8.9.jar" (
    echo ========================================================
    echo  [ERROR] Cosmic Client files could not be found!
    echo ========================================================
    echo.
    echo If you opened this file directly from inside a ZIP or RAR archive:
    echo   1. Close this window.
    echo   2. Right-click the ZIP archive and select "Extract All...".
    echo   3. Open the extracted folder and double-click START-HERE-WINDOWS.bat!
    echo.
    powershell -Command "[System.Windows.Forms.MessageBox]::Show('Cosmic Client must be extracted before running!\n\n1. Close this window\n2. Right-click the ZIP archive and select ''Extract All...''\n3. Open the extracted folder and run START-HERE-WINDOWS.bat', 'Cosmic Client - Extraction Required', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Warning)" 2>nul
    pause
    exit /b 1
)

:: 1. If standalone compiled Electron executable is in dist, launch directly
if exist "%~dp0dist\win-unpacked\CosmicClient.exe" (
    echo Starting Cosmic Client Launcher (Standalone)...
    start "" "%~dp0dist\win-unpacked\CosmicClient.exe"
    exit /b 0
)
if exist "%~dp0dist\win-unpacked\CosmicClientOffcloud.exe" (
    echo Starting Cosmic Client Offcloud Launcher (Standalone)...
    start "" "%~dp0dist\win-unpacked\CosmicClientOffcloud.exe"
    exit /b 0
)

:: 2. Check for Node.js / NPM
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [Launcher] Node.js is not detected on your system.
    echo [Launcher] Starting automatic Node.js portable bootstrap...
    if not exist ".tools\node" (
        mkdir ".tools\node" 2>nul
        echo [Launcher] Downloading portable Node.js LTS...
        powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = '.tools\node\node.zip'; (New-Object Net.WebClient).DownloadFile('https://nodejs.org/dist/v20.18.0/node-v20.18.0-win-x64.zip', $zip); Expand-Archive -Path $zip -DestinationPath '.tools\node' -Force; Remove-Item $zip"
        for /d %%D in (.tools\node\node-v*) do (
            move "%%D\*" ".tools\node\" >nul 2>nul
            rmdir "%%D" >nul 2>nul
        )
    )
    set "PATH=%~dp0.tools\node;%PATH%"
)

:: 3. Check for dependencies
if not exist "node_modules" (
    echo [Launcher] Installing dependencies (first run)...
    call npm install
)

echo Starting Cosmic Client Launcher...
npm start
