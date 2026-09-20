Add-Type -AssemblyName System.Drawing

$baseDir = (Get-Item $PSScriptRoot).Parent.FullName
$srcPngPath = Join-Path $baseDir "extracted_ico32.png"
$outIcoPath = Join-Path $baseDir "cosmic.ico"
$outPngPath = Join-Path $baseDir "cosmic-icon.png"

# If extracted_ico32.png is not found, extract from Launcher.jar or scratch
if (-not (Test-Path $srcPngPath)) {
    $scratchPng = Join-Path $baseDir "scratch\ico32.png"
    if (Test-Path $scratchPng) {
        Copy-Item $scratchPng $srcPngPath -Force
    } else {
        $launcherJar = Join-Path $baseDir "CosmicClient-x64\Launcher.jar"
        if (Test-Path $launcherJar) {
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $zip = [System.IO.Compression.ZipFile]::OpenRead($launcherJar)
            $entry = $zip.GetEntry("ico32.png")
            if ($entry) {
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $srcPngPath, $true)
            }
            $zip.Dispose()
        }
    }
}

if (-not (Test-Path $srcPngPath)) {
    Write-Error "Source icon not found at $srcPngPath"
    exit 1
}

$srcImg = [System.Drawing.Image]::FromFile($srcPngPath)
$sizes = @(16, 24, 32, 48, 64, 128, 256)
$pngList = @()

foreach ($sz in $sizes) {
    $bmp = New-Object System.Drawing.Bitmap $sz, $sz, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($srcImg, 0, 0, $sz, $sz)
    $g.Dispose()
    
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngList += ,@($sz, $ms.ToArray())
    
    if ($sz -eq 256) {
        $bmp.Save($outPngPath, [System.Drawing.Imaging.ImageFormat]::Png)
    }

    # Save 16x16 and 32x32 for game window and agent
    if ($sz -eq 16) {
        $agentRes16 = Join-Path $baseDir "cosmic-agent-src\src\main\resources\cosmic-icon-16.png"
        [System.IO.Directory]::CreateDirectory((Split-Path $agentRes16)) | Out-Null
        $bmp.Save($agentRes16, [System.Drawing.Imaging.ImageFormat]::Png)
        
        # Replace Minecraft asset hash for 16x16 icon
        $asset16 = Join-Path $baseDir "CosmicClient-x64\assets_18\objects\bd\bdf48ef6b5d0d23bbb02e17d04865216179f510a"
        if (Test-Path (Split-Path $asset16)) {
            $bmp.Save($asset16, [System.Drawing.Imaging.ImageFormat]::Png)
        }
    }
    if ($sz -eq 32) {
        $agentRes32 = Join-Path $baseDir "cosmic-agent-src\src\main\resources\cosmic-icon-32.png"
        [System.IO.Directory]::CreateDirectory((Split-Path $agentRes32)) | Out-Null
        $bmp.Save($agentRes32, [System.Drawing.Imaging.ImageFormat]::Png)

        # Replace Minecraft asset hash for 32x32 icon
        $asset32 = Join-Path $baseDir "CosmicClient-x64\assets_18\objects\92\92750c5f93c312ba9ab413d546f32190c56d6f1f"
        if (Test-Path (Split-Path $asset32)) {
            $bmp.Save($asset32, [System.Drawing.Imaging.ImageFormat]::Png)
        }
    }

    $bmp.Dispose()
}

$srcImg.Dispose()

$fs = [System.IO.File]::Create($outIcoPath)
$bw = New-Object System.IO.BinaryWriter $fs

# ICO Header
$bw.Write([uint16]0) # Reserved
$bw.Write([uint16]1) # Type ICO
$bw.Write([uint16]$pngList.Count) # Count

$offset = 6 + (16 * $pngList.Count)
foreach ($item in $pngList) {
    $sz = $item[0]
    $bytes = $item[1]
    
    $bw.Write([byte]$(if ($sz -ge 256) { 0 } else { $sz })) # Width
    $bw.Write([byte]$(if ($sz -ge 256) { 0 } else { $sz })) # Height
    $bw.Write([byte]0) # Color count
    $bw.Write([byte]0) # Reserved
    $bw.Write([uint16]1) # Planes
    $bw.Write([uint16]32) # Bit count
    $bw.Write([uint32]$bytes.Length) # Bytes in res
    $bw.Write([uint32]$offset) # Image offset
    
    $offset += $bytes.Length
}

foreach ($item in $pngList) {
    $bw.Write($item[1])
}

$bw.Dispose()
$fs.Dispose()

Write-Host "Successfully generated authentic multi-resolution cosmic.ico with $($pngList.Count) layers at $outIcoPath!" -ForegroundColor Green
