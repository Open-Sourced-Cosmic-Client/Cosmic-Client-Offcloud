Add-Type -AssemblyName System.Drawing

$baseDir = (Get-Item $PSScriptRoot).Parent.FullName
$outIcoPath = Join-Path $baseDir "cosmic.ico"
$outPngPath = Join-Path $baseDir "cosmic-icon.png"

Write-Host "Rendering Ultra-HD Vector-Grade Cosmic Client Icon..." -ForegroundColor Cyan

function DrawCosmicLogo($size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $scale = $size / 256.0
    $cx = $size / 2.0
    $cy = $size / 2.0

    # 1. Subtle Outer Glow Aura
    $glowRad = 118.0 * $scale
    $glowRect = New-Object System.Drawing.RectangleF ($cx - $glowRad), ($cy - $glowRad), ($glowRad * 2), ($glowRad * 2)
    $pathGlow = New-Object System.Drawing.Drawing2D.GraphicsPath
    $pathGlow.AddEllipse($glowRect)
    $pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush $pathGlow
    $pgb.CenterColor = [System.Drawing.Color]::FromArgb(45, 139, 92, 246) # Soft purple glow
    $pgb.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 11, 13, 23))
    $g.FillPath($pgb, $pathGlow)
    $pathGlow.Dispose()
    $pgb.Dispose()

    # 2. Back Half of the Orbital Ring (Behind Planet)
    $ringWidth = 220.0 * $scale
    $ringHeight = 72.0 * $scale
    $ringThickness = [math]::Max(2.5, 14.0 * $scale)

    $stateBeforeRing = $g.Save()
    $g.TranslateTransform($cx, $cy)
    $g.RotateTransform(-28.0)

    # Clip upper half for the back of the ring
    $g.SetClip((New-Object System.Drawing.RectangleF (-$ringWidth), (-$ringHeight), ($ringWidth * 2), $ringHeight))
    
    $penBack = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(180, 236, 72, 153)), $ringThickness # Magenta/Pink
    $penBack.DashCap = [System.Drawing.Drawing2D.DashCap]::Round
    $ringRect = New-Object System.Drawing.RectangleF ((-$ringWidth / 2.0)), ((-$ringHeight / 2.0)), $ringWidth, $ringHeight
    $g.DrawEllipse($penBack, $ringRect)
    $penBack.Dispose()

    $g.Restore($stateBeforeRing)

    # 3. Main Celestial Sphere (Planet Body)
    $planetRad = 74.0 * $scale
    $planetRect = New-Object System.Drawing.RectangleF ($cx - $planetRad), ($cy - $planetRad), ($planetRad * 2), ($planetRad * 2)
    
    # Sphere gradient: deep violet (#6D28D9) to vibrant cyan (#06B6D4)
    $brushPlanet = New-Object System.Drawing.Drawing2D.LinearGradientBrush (
        (New-Object System.Drawing.PointF ($cx - $planetRad), ($cy - $planetRad)),
        (New-Object System.Drawing.PointF ($cx + $planetRad), ($cy + $planetRad)),
        [System.Drawing.Color]::FromArgb(255, 109, 40, 217), # Deep violet
        [System.Drawing.Color]::FromArgb(255, 6, 182, 212)    # Cyan
    )
    $brushPlanet.SetBlendTriangularShape(0.6)
    $g.FillEllipse($brushPlanet, $planetRect)
    $brushPlanet.Dispose()

    # Outer border on planet
    $penPlanet = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(220, 192, 132, 252)), ([math]::Max(1.0, 3.5 * $scale))
    $g.DrawEllipse($penPlanet, $planetRect)
    $penPlanet.Dispose()

    # 4. Front Half of the Orbital Ring (In Front of Planet)
    $stateFrontRing = $g.Save()
    $g.TranslateTransform($cx, $cy)
    $g.RotateTransform(-28.0)

    # Clip lower half for front of the ring
    $g.SetClip((New-Object System.Drawing.RectangleF (-$ringWidth), 0, ($ringWidth * 2), $ringHeight))

    $brushRing = New-Object System.Drawing.Drawing2D.LinearGradientBrush (
        (New-Object System.Drawing.PointF (-$ringWidth / 2.0), 0),
        (New-Object System.Drawing.PointF ($ringWidth / 2.0), 0),
        [System.Drawing.Color]::FromArgb(255, 236, 72, 153), # Hot pink
        [System.Drawing.Color]::FromArgb(255, 139, 92, 246)  # Violet
    )
    $penFront = New-Object System.Drawing.Pen $brushRing, $ringThickness
    $g.DrawEllipse($penFront, $ringRect)
    $penFront.Dispose()
    $brushRing.Dispose()

    # Ring Inner Highlight Line
    $penInner = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(160, 255, 255, 255)), ([math]::Max(0.8, 2.2 * $scale))
    $g.DrawEllipse($penInner, $ringRect)
    $penInner.Dispose()

    $g.Restore($stateFrontRing)

    # 5. Glowing Central Core (Cosmic Singularity)
    $coreRad = 26.0 * $scale
    $coreRect = New-Object System.Drawing.RectangleF ($cx - $coreRad), ($cy - $coreRad), ($coreRad * 2), ($coreRad * 2)
    $brushCore = New-Object System.Drawing.Drawing2D.LinearGradientBrush (
        (New-Object System.Drawing.PointF ($cx - $coreRad), ($cy - $coreRad)),
        (New-Object System.Drawing.PointF ($cx + $coreRad), ($cy + $coreRad)),
        [System.Drawing.Color]::FromArgb(255, 243, 232, 255), # White-lavender
        [System.Drawing.Color]::FromArgb(255, 168, 85, 247)   # Vibrant purple
    )
    $g.FillEllipse($brushCore, $coreRect)
    $brushCore.Dispose()

    # Core rim glow
    $penCore = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(240, 255, 255, 255)), ([math]::Max(1.0, 2.0 * $scale))
    $g.DrawEllipse($penCore, $coreRect)
    $penCore.Dispose()

    # 6. Star sparkles on high resolutions
    if ($size -ge 48) {
        $brushStar = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(230, 255, 255, 255))
        # Top right star
        $g.FillEllipse($brushStar, ($cx + 70.0 * $scale), ($cy - 75.0 * $scale), (5.0 * $scale), (5.0 * $scale))
        # Bottom left star
        $g.FillEllipse($brushStar, ($cx - 85.0 * $scale), ($cy + 60.0 * $scale), (4.0 * $scale), (4.0 * $scale))
        # Top left twinkle
        $g.FillEllipse($brushStar, ($cx - 65.0 * $scale), ($cy - 68.0 * $scale), (3.0 * $scale), (3.0 * $scale))
        $brushStar.Dispose()
    }

    $g.Dispose()
    return $bmp
}

# Generate 256x256 Master PNG
$masterBmp = DrawCosmicLogo 256
$masterBmp.Save($outPngPath, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Saved Master High-Res PNG -> $outPngPath" -ForegroundColor Green

# Generate ICO with all standard Windows resolutions (16, 24, 32, 48, 64, 128, 256)
$sizes = @(16, 24, 32, 48, 64, 128, 256)
$pngList = @()

foreach ($sz in $sizes) {
    $rendered = DrawCosmicLogo $sz
    $ms = New-Object System.IO.MemoryStream
    $rendered.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngList += ,@($sz, $ms.ToArray())
    $rendered.Dispose()
}

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

# Also copy to src/assets and CosmicClient-x64
Copy-Item $outIcoPath (Join-Path $baseDir "src\assets\cosmic.ico") -Force
Copy-Item $outPngPath (Join-Path $baseDir "src\assets\cosmic-icon.png") -Force

Write-Host "Successfully generated vector-perfect multi-resolution cosmic.ico!" -ForegroundColor Green
