<#
    Turns the AI-generated source art in icons/ into the exact assets Play wants:

      play-assets/icon-512.png         512x512 32-bit PNG, fully opaque
      play-assets/feature-graphic.png  1024x500, title and tagline composited on

    The feature graphic source is a wider ratio than Play accepts, so it is
    CROPPED to 2.048:1 rather than squashed - stretching a radial glow is very
    visible.

    Run:  powershell -ExecutionPolicy Bypass -File tools\prepare-store-graphics.ps1
#>
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Definition)
$srcDir = Join-Path $root 'icons'
$out = Join-Path $root 'play-assets'
New-Item -ItemType Directory -Force -Path $out | Out-Null

$cream = [System.Drawing.Color]::FromArgb(255, 243, 246, 249)
$ink   = [System.Drawing.Color]::FromArgb(255, 22, 30, 41)
$mint  = [System.Drawing.Color]::FromArgb(255, 93, 226, 184)
$muted = [System.Drawing.Color]::FromArgb(255, 174, 187, 203)

function Get-Source([string]$pattern, [double]$minRatio, [double]$maxRatio) {
    $match = Get-ChildItem $srcDir -Filter *.png | ForEach-Object {
        $img = [System.Drawing.Image]::FromFile($_.FullName)
        $r = $img.Width / $img.Height
        $img.Dispose()
        [PSCustomObject]@{ File = $_; Ratio = $r }
    } | Where-Object { $_.Ratio -ge $minRatio -and $_.Ratio -le $maxRatio } |
        Sort-Object { $_.File.LastWriteTime } -Descending | Select-Object -First 1
    if (-not $match) { throw "No source image in icons/ with ratio $minRatio-$maxRatio ($pattern)" }
    return $match.File.FullName
}

function New-Graphics($bmp) {
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    return $g
}

# The cat head from the app's own launcher icon, in its 24x24 design space.
function Draw-Cat($g, [double]$s, [double]$dx, [double]$dy, $fill, $eyeColor) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    function P([double]$x, [double]$y) {
        New-Object System.Drawing.PointF(([float]($x * $s + $dx)), ([float]($y * $s + $dy)))
    }
    $p.AddLine((P 5 4), (P 9.2 7.4))
    $p.AddBezier((P 9.2 7.4), (P 10.1 7.1), (P 11 7), (P 12 7))
    $p.AddBezier((P 12 7), (P 13 7), (P 13.9 7.1), (P 14.8 7.4))
    $p.AddLine((P 14.8 7.4), (P 19 4))
    $p.AddLine((P 19 4), (P 18.2 9))
    $p.AddBezier((P 18.2 9), (P 19.3 10.2), (P 20 11.7), (P 20 13.4))
    $p.AddBezier((P 20 13.4), (P 20 17), (P 16.4 20), (P 12 20))
    $p.AddBezier((P 12 20), (P 7.6 20), (P 4 17), (P 4 13.4))
    $p.AddBezier((P 4 13.4), (P 4 11.7), (P 4.7 10.2), (P 5.8 9))
    $p.CloseFigure()

    $b = New-Object System.Drawing.SolidBrush($fill)
    $g.FillPath($b, $p)
    $b.Dispose(); $p.Dispose()

    $eye = New-Object System.Drawing.SolidBrush($eyeColor)
    foreach ($ex in 9.3, 14.7) {
        $g.FillEllipse($eye,
            [float](($ex - 1.15) * $s + $dx), [float]((12.4 - 1.45) * $s + $dy),
            [float](2.3 * $s), [float](2.9 * $s))
    }
    $eye.Dispose()
}

# ------------------------------------------------------------------ app icon
$iconSrc = Get-Source 'square' 0.98 1.02
$src = [System.Drawing.Image]::FromFile($iconSrc)

# 32bpp with a fully opaque alpha channel: Play asks for a 32-bit PNG, and a
# genuinely transparent icon renders badly on light backgrounds.
$icon = New-Object System.Drawing.Bitmap(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = New-Graphics $icon
$g.Clear($ink)
$g.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, 512, 512)),
    0, 0, $src.Width, $src.Height, [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose(); $src.Dispose()
$icon.Save((Join-Path $out 'icon-512.png'), [System.Drawing.Imaging.ImageFormat]::Png)

# Legibility check: what it looks like in a search result.
$small = New-Object System.Drawing.Bitmap(48, 48)
$g = New-Graphics $small
$g.DrawImage($icon, 0, 0, 48, 48)
$g.Dispose()
$small.Save((Join-Path $out 'icon-48-preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$small.Dispose(); $icon.Dispose()

# ----------------------------------------------------------- feature graphic
$featSrc = Get-Source 'wide' 1.6 3.5
$src = [System.Drawing.Image]::FromFile($featSrc)

# Crop to Play's 2.048:1 from the LEFT edge, so the radial glow - which the
# generator put on the left - survives intact. Cropping the other way would
# clip it in half.
$targetRatio = 1024.0 / 500.0
$cropW = [int][math]::Round($src.Height * $targetRatio)
$cropH = $src.Height
if ($cropW -gt $src.Width) {
    $cropW = $src.Width
    $cropH = [int][math]::Round($src.Width / $targetRatio)
}

$feat = New-Object System.Drawing.Bitmap(1024, 500, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = New-Graphics $feat
$g.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, 1024, 500)),
    0, 0, $cropW, $cropH, [System.Drawing.GraphicsUnit]::Pixel)
$src.Dispose()

# Cat mark sitting inside the glow, sized to the banner height.
$scale = 210.0 / 24
Draw-Cat $g $scale 92 145 $cream $ink

# Text is drawn here rather than asked of the image model, which garbles letters.
$title = New-Object System.Drawing.Font('Segoe UI', 60, [System.Drawing.FontStyle]::Bold)
$sub   = New-Object System.Drawing.Font('Segoe UI', 26, [System.Drawing.FontStyle]::Regular)
$small = New-Object System.Drawing.Font('Segoe UI', 20, [System.Drawing.FontStyle]::Regular)
$bCream = New-Object System.Drawing.SolidBrush($cream)
$bMint  = New-Object System.Drawing.SolidBrush($mint)
$bMuted = New-Object System.Drawing.SolidBrush($muted)

$dot = [string][char]0x2022
$g.DrawString('CatGuard', $title, $bCream, 390, 152)
$g.DrawString('Keeps cats out of your space', $sub, $bMint, 398, 243)
$g.DrawString("On-device AI camera   $dot   Works offline", $small, $bMuted, 398, 292)

$title.Dispose(); $sub.Dispose(); $small.Dispose()
$bCream.Dispose(); $bMint.Dispose(); $bMuted.Dispose()
$g.Dispose()
$feat.Save((Join-Path $out 'feature-graphic.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$feat.Dispose()

Write-Output "Sources:"
Write-Output "  icon    <- $(Split-Path -Leaf $iconSrc)"
Write-Output "  feature <- $(Split-Path -Leaf $featSrc)  (cropped ${cropW}x${cropH} then scaled)"
Write-Output ""
Get-ChildItem $out -Filter *.png | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    "{0,-24} {1}x{2}  {3}  {4} KB" -f $_.Name, $img.Width, $img.Height, $img.PixelFormat, [math]::Round($_.Length / 1KB)
    $img.Dispose()
}
