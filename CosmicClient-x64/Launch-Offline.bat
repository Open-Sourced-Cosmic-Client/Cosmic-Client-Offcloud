@echo off
setlocal enabledelayedexpansion
title Cosmic Client Offcloud - Direct Launch

echo ========================================================
echo   Cosmic Client Offcloud - Pure Offline Mode
echo ========================================================

set "INSTALL_DIR=%~dp0"
if "%INSTALL_DIR:~-1%"=="\" set "INSTALL_DIR=%INSTALL_DIR:~0,-1%"
cd /d "%INSTALL_DIR%"

:: 1. Check Java binary
set "JAVA_EXE=%INSTALL_DIR%\bootstrap\java\bin\java.exe"
if not exist "%JAVA_EXE%" (
    for /r "%INSTALL_DIR%\bootstrap" %%F in (java.exe) do (
        if exist "%%F" (
            set "JAVA_EXE=%%F"
            goto :java_found
        )
    )
)
:java_found

if not exist "%JAVA_EXE%" (
    if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set "JAVA_EXE=C:\Program Files\Java\jdk-21\bin\java.exe"
    ) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
        set "JAVA_EXE=C:\Program Files\Java\jdk-17\bin\java.exe"
    ) else if exist "C:\Program Files\Java\jdk-22\bin\java.exe" (
        set "JAVA_EXE=C:\Program Files\Java\jdk-22\bin\java.exe"
    ) else if exist "C:\Program Files\Java\jdk-24\bin\java.exe" (
        set "JAVA_EXE=C:\Program Files\Java\jdk-24\bin\java.exe"
    ) else (
        where java >nul 2>nul
        if %ERRORLEVEL% EQU 0 (
            set "JAVA_EXE=java"
        )
    )
)

:: Auto-download Zulu 19 if missing
if "%JAVA_EXE%"=="" (
    echo [Launcher] No Java runtime detected. Downloading Zulu 19 JRE...
    mkdir "%INSTALL_DIR%\bootstrap" 2>nul
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = '%INSTALL_DIR%\bootstrap\temp_jre.zip'; (New-Object Net.WebClient).DownloadFile('https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-win_x64.zip', $zip); Expand-Archive -Path $zip -DestinationPath '%INSTALL_DIR%\bootstrap' -Force; Remove-Item $zip; $dir = Get-ChildItem '%INSTALL_DIR%\bootstrap' -Directory -Filter 'zulu*' | Select-Object -First 1; if ($dir) { Rename-Item $dir.FullName '%INSTALL_DIR%\bootstrap\java' }"
    set "JAVA_EXE=%INSTALL_DIR%\bootstrap\java\bin\java.exe"
)

:: 2. Check Agent
set "AGENT_JAR=%INSTALL_DIR%\cosmic-agent.jar"
if not exist "%AGENT_JAR%" set "AGENT_JAR=%INSTALL_DIR%\..\cosmic-agent.jar"

:: 3. Native Libraries & Paths
set "NATIVES_DIR=%INSTALL_DIR%\1.8\bin-1.8"
set "CLIENT_JAR=%INSTALL_DIR%\1.8\CosmicClient-1.8.9.jar"
set "ASSETS_DIR=%INSTALL_DIR%\assets_18"
set "BOOTSTRAP_JAR=%INSTALL_DIR%\Launcher.jar"

echo Java:    %JAVA_EXE%
echo Agent:   %AGENT_JAR%
echo Natives: %NATIVES_DIR%
echo Client:  %CLIENT_JAR%
echo.

"%JAVA_EXE%" ^
  "-Dcosmic.java=%INSTALL_DIR%\bootstrap\java" ^
  "-Dcosmic.installdir=%INSTALL_DIR%" ^
  "-Dcosmic.bootstrap=%BOOTSTRAP_JAR%" ^
  "-Dcosmic.launcher.load=v1" ^
  "-Dcosmic.log.plain=true" ^
  "-Dcosmic.version=2.7.0.b84ff" ^
  "-Dcosmic.branch=master" ^
  "-Dapple.awt.application.name=Cosmic Client" ^
  "-Dapple.awt.application.appearance=system" ^
  "-Duser.language=en-US" ^
  "-Djava.library.path=%NATIVES_DIR%" ^
  -Xms2048M ^
  -Xmx4096M ^
  -Xmn128M ^
  -XX:MaxDirectMemorySize=4096M ^
  -XX:+DisableAttachMechanism ^
  -Xshare:off ^
  --add-opens java.desktop/java.awt.event=ALL-UNNAMED ^
  --add-opens java.desktop/sun.awt=ALL-UNNAMED ^
  --add-opens java.management/sun.management=ALL-UNNAMED ^
  "-Dcosmic.offline=true" ^
  "-javaagent:%AGENT_JAR%" ^
  -XX:+UseG1GC ^
  -XX:+ParallelRefProcEnabled ^
  -XX:MaxGCPauseMillis=25 ^
  -XX:+UnlockExperimentalVMOptions ^
  -XX:+DisableExplicitGC ^
  -XX:+AlwaysPreTouch ^
  -jar "%CLIENT_JAR%" ^
  --version 1.8 ^
  --assetsDir "%ASSETS_DIR%" ^
  --assetIndex 1.8 ^
  --username CosmicPlayer ^
  --uuid 00000000-0000-0000-0000-000000000000 ^
  --accessToken offline ^
  --userType msa ^
  --versionType CosmicClient ^
  --userProperties {} ^
  --width 1280 ^
  --height 720 ^
  --novid

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Game exited with code %ERRORLEVEL%
    pause
)
