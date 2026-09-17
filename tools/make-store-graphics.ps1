<#
    Renders the Play Store graphics that can be produced without a device:
      play-assets/icon-512.png            512x512  app icon (required)
      play-assets/feature-graphic.png    1024x500  feature graphic (required)

    Screenshots cannot be generated here - Play wants real ones from a phone.

    Run:  powershell -ExecutionPolicy Bypass -File tools\make-store-graphics.ps1
#>
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Definition)
$out = Join-Path $root 'play-assets'
New-Item -ItemType Directory -Force -Path $out | Out-Null

$ink   = [System.Drawing.Color]::FromArgb(255, 27, 36, 48)    # #1B2430
$ink2  = [System.Drawing.Color]::FromArgb(255, 16, 22, 31)    # #10161F
$cream = [System.Drawing.Color]::FromArgb(255, 243, 246, 249) # #F3F6F9
$mint  = [System.Drawing.Color]::FromArgb(255, 77, 208, 167)  # #4DD0A7

# The same cat-head outline the launcher icon uses, in its 24x24 design space.
function New-CatPath([double]$s, [double]$dx, [double]$dy) {
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
    return $p
}

function Draw-Cat($g, [double]$s, [double]$dx, [double]$dy, $fill, $eyeColor) {
    $path = New-CatPath $s $dx $dy
    $brush = New-Object System.Drawing.SolidBrush($fill)
    $g.FillPath($brush, $path)
    $brush.Dispose(); $path.Dispose()

    $eye = New-Object System.Drawing.SolidBrush($eyeColor)
    foreach ($ex in 9.3, 14.7) {
        $g.FillEllipse(
            $eye,
            [float](($ex - 1.15) * $s + $dx), [float](((12.4) - 1.45) * $s + $dy),
            [float](2.3 * $s), [float](2.9 * $s))
    }
    $eye.Dispose()
}

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    return @($bmp, $g)
}

# ---------------------------------------------------------------- 512 icon
$r = New-Canvas 512 512
$bmp = $r[0]; $g = $r[1]
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(0, 0)),
    (New-Object System.Drawing.Point(512, 512)), $ink, $ink2)
$g.FillRectangle($bg, 0, 0, 512, 512)
$bg.Dispose()
# 24-unit artwork centred with a comfortable margin
$scale = 512 * 0.68 / 24
Draw-Cat $g $scale (([double]512 - 24 * $scale) / 2) (([double]512 - 24 * $scale) / 2 + 8) $cream $ink
$bmp.Save((Join-Path $out 'icon-512.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# ------------------------------------------------------ 1024x500 feature graphic
$r = New-Canvas 1024 500
$bmp = $r[0]; $g = $r[1]
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(0, 0)),
    (New-Object System.Drawing.Point(1024, 500)), $ink, $ink2)
$g.FillRectangle($bg, 0, 0, 1024, 500)
$bg.Dispose()

# Soft mint glow behind the mark
$glow = New-Object System.Drawing.Drawing2D.GraphicsPath
$glow.AddEllipse(60, 90, 320, 320)
$pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush($glow)
$pgb.CenterColor = [System.Drawing.Color]::FromArgb(48, 77, 208, 167)
$pgb.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 77, 208, 167))
$g.FillPath($pgb, $glow)
$pgb.Dispose(); $glow.Dispose()

$scale = 250.0 / 24
Draw-Cat $g $scale 135 130 $cream $ink

$title = New-Object System.Drawing.Font('Segoe UI', 62, [System.Drawing.FontStyle]::Bold)
$sub   = New-Object System.Drawing.Font('Segoe UI', 27, [System.Drawing.FontStyle]::Regular)
$small = New-Object System.Drawing.Font('Segoe UI', 21, [System.Drawing.FontStyle]::Regular)
$bCream = New-Object System.Drawing.SolidBrush($cream)
$bMint  = New-Object System.Drawing.SolidBrush($mint)
$bMuted = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 174, 187, 203))

$g.DrawString('CatGuard', $title, $bCream, 430, 150)
$g.DrawString('Keeps cats out of your space', $sub, $bMint, 438, 245)
# The separator is built from a char code on purpose: a literal middle dot in
# this file gets mangled when PowerShell reads it under a non-UTF8 code page.
$dot = [string][char]0x2022
$g.DrawString("On-device AI camera   $dot   Works offline", $small, $bMuted, 438, 300)

$title.Dispose(); $sub.Dispose(); $small.Dispose()
$bCream.Dispose(); $bMint.Dispose(); $bMuted.Dispose()
$bmp.Save((Join-Path $out 'feature-graphic.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

Get-ChildItem $out -Filter *.png | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    "{0,-22} {1}x{2}  {3} KB" -f $_.Name, $img.Width, $img.Height, [math]::Round($_.Length / 1KB)
    $img.Dispose()
}
