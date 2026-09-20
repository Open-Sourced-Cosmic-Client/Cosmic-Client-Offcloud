Add-Type -AssemblyName System.Drawing

$baseDir = (Get-Item $PSScriptRoot).Parent.FullName
$iconPath = Join-Path $baseDir "src\assets\cosmic-icon.png"
$logoPath = Join-Path $baseDir "src\assets\cosmic-logo.png"
$outputPath = Join-Path $baseDir "splash.bmp"

$width = 480
$height = 320

$bmp = New-Object System.Drawing.Bitmap($width, $height, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)

$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

# 1. Dark Obsidian Cosmic Background #06070D
$bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(6, 7, 13))
$g.FillRectangle($bgBrush, 0, 0, $width, $height)

# 2. Subtle Nebula Radial Glow
$glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$glowPath.AddEllipse(40, 20, 400, 280)
$pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath)
$pgb.CenterColor = [System.Drawing.Color]::FromArgb(40, 139, 92, 246)
$pgb.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 6, 7, 13))
$g.FillPath($pgb, $glowPath)
$pgb.Dispose()
$glowPath.Dispose()

# 3. Outer Border
$borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(90, 139, 92, 246), 2)
$g.DrawRectangle($borderPen, 1, 1, $width - 2, $height - 2)
$borderPen.Dispose()

# 4. Draw Cosmic Planet Icon
if (Test-Path $iconPath) {
    $iconImg = [System.Drawing.Image]::FromFile($iconPath)
    $iconSize = 72
    $iconX = [int](($width - $iconSize) / 2)
    $iconY = 38
    $g.DrawImage($iconImg, $iconX, $iconY, $iconSize, $iconSize)
    $iconImg.Dispose()
}

# 5. Draw Classic Cosmic Logo
if (Test-Path $logoPath) {
    $logoImg = [System.Drawing.Image]::FromFile($logoPath)
    $logoW = 280
    $logoH = [int]($logoImg.Height * ($logoW / $logoImg.Width))
    $logoX = [int](($width - $logoW) / 2)
    $logoY = 125
    $g.DrawImage($logoImg, $logoX, $logoY, $logoW, $logoH)
    $logoImg.Dispose()
}

# 6. Loading status bar track
$trackX = 40
$trackY = 225
$trackW = 400
$trackH = 6
$trackBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(30, 255, 255, 255))
$g.FillRectangle($trackBrush, $trackX, $trackY, $trackW, $trackH)
$trackBrush.Dispose()

# 7. Loading status bar fill (simulated 70% progress glow)
$fillW = 280
$fillBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point($trackX, $trackY)),
    (New-Object System.Drawing.Point(($trackX + $fillW), $trackY)),
    [System.Drawing.Color]::FromArgb(99, 102, 241),
    [System.Drawing.Color]::FromArgb(236, 72, 153)
)
$g.FillRectangle($fillBrush, $trackX, $trackY, $fillW, $trackH)
$fillBrush.Dispose()

# 8. Status Text
$fontStatus = New-Object System.Drawing.Font("Segoe UI", 9, [System.Drawing.FontStyle]::Bold)
$textBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(203, 213, 225))
$format = New-Object System.Drawing.StringFormat
$format.Alignment = [System.Drawing.StringAlignment]::Center
$g.DrawString("STARTING COSMIC CLIENT...", $fontStatus, $textBrush, ($width / 2), 245, $format)
$textBrush.Dispose()
$fontStatus.Dispose()

# 9. Subtitle / Footer Text
$fontSub = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Regular)
$subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(100, 116, 139))
$g.DrawString("Off-Cloud Archive - Built for Cosmonauts", $fontSub, $subBrush, ($width / 2), 282, $format)
$subBrush.Dispose()
$fontSub.Dispose()
$format.Dispose()

$g.Dispose()

# Save as pure 24bpp BMP for NSIS compatibility
$bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Bmp)
$bmp.Dispose()

Write-Host "[Splash] Generated high-res splash.bmp successfully at $outputPath" -ForegroundColor Green
