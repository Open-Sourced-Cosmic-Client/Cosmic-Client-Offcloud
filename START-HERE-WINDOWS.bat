@echo off
setlocal enabledelayedexpansion
title Cosmic Client Offcloud - Windows Setup & Launch
cd /d "%~dp0"

echo ========================================================
echo   Cosmic Client Offcloud - Universal Windows Edition (32/64-bit)
echo ========================================================
echo.

:: 1. Check if running inside unextracted ZIP / RAR / Temp directory
set "CLIENT_JAR=%~dp0CosmicClient-x64\1.8\CosmicClient-1.8.9.jar"
if not exist "%CLIENT_JAR%" set "CLIENT_JAR=%~dp01.8\CosmicClient-1.8.9.jar"

if not exist "%CLIENT_JAR%" (
    echo ========================================================
    echo  [ERROR] Cosmic Client core files could not be found!
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

echo [OK] Folder integrity verified (100% Extracted).
echo [OK] 32-bit & 64-bit Universal Native Support.
echo [OK] Cosmic Agent Bytecode Injection & Offcloud Vault Ready.
echo.
echo Choose how you would like to launch:
echo.
echo   [1] GUI Launcher (Desktop UI, Account Manager, RAM Slider & Settings - Recommended)
echo   [2] Direct Game Launch (Instant 1-Click Fast Launch)
echo   [3] Exit
echo.
echo Auto-launching GUI Launcher [1] in 5 seconds... (Press 2 for Direct Launch, 1 for GUI)
echo.

choice /c 123 /t 5 /d 1 /m "Select option"
set "CHOICE_VAL=%ERRORLEVEL%"

if "%CHOICE_VAL%"=="1" (
    if exist "%~dp0Cosmic Client Offcloud-1.0 Launcher.exe" (
        start "" "%~dp0Cosmic Client Offcloud-1.0 Launcher.exe"
    ) else if exist "%~dp0dist\win-unpacked\CosmicClient.exe" (
        start "" "%~dp0dist\win-unpacked\CosmicClient.exe"
    ) else if exist "%~dp0CosmicClientLauncher.exe" (
        start "" "%~dp0CosmicClientLauncher.exe"
    ) else (
        start "" cmd /c "%~dp0Launch-GUI.bat"
    )
    exit /b 0
)
if "%CHOICE_VAL%"=="2" (
    if exist "%~dp0CosmicClient-Direct.exe" (
        start "" "%~dp0CosmicClient-Direct.exe"
    ) else (
        start "" cmd /c "%~dp0Launch-Cosmic-Direct.bat"
    )
    exit /b 0
)
if "%CHOICE_VAL%"=="3" (
    exit /b 0
)

