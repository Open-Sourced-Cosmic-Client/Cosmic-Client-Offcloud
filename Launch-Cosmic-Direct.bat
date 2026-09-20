@echo off
setlocal enabledelayedexpansion
title Cosmic Client Offcloud - Direct Launch
cd /d "%~dp0"

:: 0. Sanitize environment variables against rogue agent injection
set "_JAVA_OPTIONS="
set "JAVA_TOOL_OPTIONS="
set "JAVA_OPTIONS="
set "IBM_JAVA_OPTIONS="


echo ========================================================
echo   Cosmic Client Offcloud - Direct Launch
echo ========================================================
echo.

:: 1. If Node.js and launcher script are available, launch via Node engine
where node >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    if exist "scripts\launch-direct.js" (
        echo [Launcher] Launching via Node.js offline engine...
        node scripts\launch-direct.js
        goto :done
    )
)

:: 2. Resolve Installation Directory
if exist "%~dp0CosmicClient-x64\1.8\CosmicClient-1.8.9.jar" (
    set "INSTALL_DIR=%~dp0CosmicClient-x64"
) else if exist "%~dp01.8\CosmicClient-1.8.9.jar" (
    set "INSTALL_DIR=%~dp0"
) else (
    set "INSTALL_DIR=%~dp0CosmicClient-x64"
)
if "%INSTALL_DIR:~-1%"=="\" set "INSTALL_DIR=%INSTALL_DIR:~0,-1%"

:: 2b. Extraction Guard Check
if not exist "%INSTALL_DIR%\1.8\CosmicClient-1.8.9.jar" (
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

:: 3. Resolve Agent JAR
set "AGENT_JAR=%~dp0cosmic-agent.jar"
if not exist "%AGENT_JAR%" set "AGENT_JAR=%INSTALL_DIR%\cosmic-agent.jar"
if not exist "%AGENT_JAR%" set "AGENT_JAR=%~dp0..\cosmic-agent.jar"

set "CLIENT_JAR=%INSTALL_DIR%\1.8\CosmicClient-1.8.9.jar"
set "NATIVES_DIR=%INSTALL_DIR%\1.8\bin-1.8"
set "ASSETS_DIR=%INSTALL_DIR%\assets_18"
set "BOOTSTRAP_JAR=%INSTALL_DIR%\Launcher.jar"

:: 4. Search for available Java Runtime
set "JAVA_EXE="
if exist "%INSTALL_DIR%\bootstrap\java\bin\java.exe" (
    set "JAVA_EXE=%INSTALL_DIR%\bootstrap\java\bin\java.exe"
)

:: Check for any extracted Java in bootstrap folder
if "%JAVA_EXE%"=="" (
    for /r "%INSTALL_DIR%\bootstrap" %%F in (java.exe) do (
        if exist "%%F" (
            set "JAVA_EXE=%%F"
            goto :java_found
        )
    )
)
:java_found

:: Check common JDK paths
if "%JAVA_EXE%"=="" (
    if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set "JAVA_EXE=C:\Program Files\Java\jdk-21\bin\java.exe"
    ) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
        set "JAVA_EXE=C:\Program Files\Java\jdk-17\bin\java.exe"
    ) else if exist "C:\Program Files\Java\jdk-22\bin\java.exe" (
        set "JAVA_EXE=C:\Program Files\Java\jdk-22\bin\java.exe"
    ) else if exist "C:\Program Files\Java\jdk-24\bin\java.exe" (
        set "JAVA_EXE=C:\Program Files\Java\jdk-24\bin\java.exe"
    ) else if exist "%USERPROFILE%\.jdks\corretto-21.0.11\bin\java.exe" (
        set "JAVA_EXE=%USERPROFILE%\.jdks\corretto-21.0.11\bin\java.exe"
    ) else (
        where java >nul 2>nul
        if %ERRORLEVEL% EQU 0 (
            set "JAVA_EXE=java"
        )
    )
)

:: 5. Auto-Download Java Zulu 19 JRE if completely missing
if "%JAVA_EXE%"=="" (
    echo [Launcher] No Java runtime detected. Starting automatic Zulu 19 JRE download...
    mkdir "%INSTALL_DIR%\bootstrap" 2>nul
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = '%INSTALL_DIR%\bootstrap\temp_jre.zip'; (New-Object Net.WebClient).DownloadFile('https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-win_x64.zip', $zip); Expand-Archive -Path $zip -DestinationPath '%INSTALL_DIR%\bootstrap' -Force; Remove-Item $zip; $dir = Get-ChildItem '%INSTALL_DIR%\bootstrap' -Directory -Filter 'zulu*' | Select-Object -First 1; if ($dir) { Rename-Item $dir.FullName '%INSTALL_DIR%\bootstrap\java' }"
    set "JAVA_EXE=%INSTALL_DIR%\bootstrap\java\bin\java.exe"
)

echo [Launcher] Java Runtime: %JAVA_EXE%
echo [Launcher] Agent JAR:    %AGENT_JAR%
echo [Launcher] Client JAR:   %CLIENT_JAR%
echo [Launcher] Natives:      %NATIVES_DIR%
echo [Launcher] Starting Cosmic Client 1.8.9 (Offcloud Direct Mode)...
echo.

cd /d "%INSTALL_DIR%"

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
  -Xms2048M -Xmx4096M -Xmn128M -XX:MaxDirectMemorySize=4096M ^
  -XX:+DisableAttachMechanism -Djdk.attach.allowAttachSelf=false -Dsun.tools.attach.enable=false -Dcosmic.hardening=true -Xshare:off ^
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

:done
echo.
echo [Launcher] Game session ended.
pause
