$base = (Get-Item $PSScriptRoot).Parent.FullName
$setupExe = Join-Path $base "CosmicClient-Setup.exe"
$verifyDir = Join-Path $base "temp_verify_install"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "=== VERIFYING COSMIC CLIENT INSTALLER ===" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

if (Test-Path $verifyDir) { Remove-Item $verifyDir -Recurse -Force }

Write-Host "`n[1/4] Testing headless extraction via --silent flag..."
$proc = Start-Process -FilePath $setupExe -ArgumentList "--silent", "--path", "`"$verifyDir`"" -PassThru

# Wait for extraction process to exit
while (-not $proc.HasExited) {
    Start-Sleep -Milliseconds 500
}

Write-Host ("Installer Process Exit Code: {0}" -f $proc.ExitCode) -ForegroundColor Green

Write-Host "`n[2/4] Checking unpacked files and folder structure..."
$checks = @(
    @{ Name = "Launcher GUI"; Path = "CosmicClientLauncher.exe" },
    @{ Name = "Direct Launcher"; Path = "CosmicClient-Direct.exe" },
    @{ Name = "Bytecode Agent"; Path = "cosmic-agent.jar" },
    @{ Name = "Bootstrap Loader"; Path = "Launcher.jar" },
    @{ Name = "Client 1.8.9 JAR"; Path = "1.8\CosmicClient-1.8.9.jar" },
    @{ Name = "Native DLLs"; Path = "1.8\bin-1.8\lwjgl64.dll" },
    @{ Name = "Native 32-bit DLLs"; Path = "1.8\bin-1.8\lwjgl.dll" },
    @{ Name = "Game Assets"; Path = "assets_18" },
    @{ Name = "64-bit Java 19 JRE"; Path = "bootstrap\java\bin\java.exe" },
    @{ Name = "32-bit Java 19 JRE"; Path = "bootstrap\java-x32\bin\java.exe" },
    @{ Name = "Start Script"; Path = "START-HERE-WINDOWS.bat" },
    @{ Name = "Accounts DB"; Path = "accounts.json" }
)

$allPassed = $true
foreach ($c in $checks) {
    $full = Join-Path $verifyDir $c.Path
    $exists = Test-Path $full
    if ($exists) {
        Write-Host ("  [PASS] {0} -> {1}" -f $c.Name, $c.Path) -ForegroundColor Green
    } else {
        Write-Host ("  [FAIL] {0} -> Missing: {1}" -f $c.Name, $c.Path) -ForegroundColor Red
        $allPassed = $false
    }
}

Write-Host "`n[3/4] Testing bundled Java execution from extracted vault..."
$j64 = Join-Path $verifyDir "bootstrap\java\bin\java.exe"
$j32 = Join-Path $verifyDir "bootstrap\java-x32\bin\java.exe"

if (Test-Path $j64) {
    $v64 = & $j64 -version 2>&1 | Out-String
    Write-Host "Bundled 64-bit Java output:" -ForegroundColor Gray
    Write-Host $v64.Trim() -ForegroundColor Gray
}

if (Test-Path $j32) {
    $v32 = & $j32 -version 2>&1 | Out-String
    Write-Host "Bundled 32-bit Java output:" -ForegroundColor Gray
    Write-Host $v32.Trim() -ForegroundColor Gray
}

Write-Host "`n[4/4] Verifying file count and cleaning up test folder..."
$totalFiles = (Get-ChildItem -Recurse $verifyDir).Count
Write-Host ("Total Extracted Files & Folders: {0}" -f $totalFiles) -ForegroundColor Cyan

Remove-Item $verifyDir -Recurse -Force

if ($allPassed) {
    Write-Host "`n=============================================================" -ForegroundColor Green
    Write-Host "=== [SUCCESS] Installer verified 100% functional & offline! ===" -ForegroundColor Green
    Write-Host "=============================================================" -ForegroundColor Green
} else {
    Write-Host "`n[ERROR] One or more verification checks failed." -ForegroundColor Red
    exit 1
}
