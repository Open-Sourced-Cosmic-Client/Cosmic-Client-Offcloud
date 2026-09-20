Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = Join-Path (Get-Location) "Cosmic-Client-Mac.zip"
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}
$macDir = (Resolve-Path "Mac").Path
[System.IO.Compression.ZipFile]::CreateFromDirectory($macDir, $zipPath, [System.IO.Compression.CompressionLevel]::Fastest, $false)
$size = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)
Write-Host "Cosmic-Client-Mac.zip created successfully ($size MB)"
