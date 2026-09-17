<#
    Renders mockups of the CatGuard screens straight from the real UI spec -
    the same colours, strings, sizes and spacing that Theme.kt, MainScreen.kt,
    SettingsScreen.kt and HistoryScreen.kt actually use.

    These are MOCKUPS, not screenshots. They are accurate enough for a README,
    and they must NOT be uploaded to Google Play as screenshots - Play requires
    genuine captures of the running app.

    Output: play-assets/mockups/*.png at 1080x2160 (360x720dp at 3x).

    Run:  powershell -ExecutionPolicy Bypass -File tools\render-screens.ps1
#>
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Definition)
$out = Join-Path $root 'play-assets\mockups'
New-Item -ItemType Directory -Force -Path $out | Out-Null

# --- density -----------------------------------------------------------------
$script:SCALE_PX = 3.0                      # 1dp = 3px, i.e. an xxhdpi 1080-wide phone
$script:SCREEN_W = [int](360 * $script:SCALE_PX)
$script:SCREEN_H = [int](720 * $script:SCALE_PX)
function dp([double]$v) { [float]($v * $script:SCALE_PX) }

# --- palette, straight from Theme.kt -----------------------------------------
function C([int]$r, [int]$g, [int]$b, [int]$a = 255) {
    [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}
$Ink        = C 0x10 0x16 0x1F   # background
$Surface    = C 0x1B 0x24 0x30
$SurfaceVar = C 0x24 0x30 0x40
$OnBg       = C 0xE6 0xEC 0xF3
$OnVar      = C 0xAE 0xBB 0xCB
$Primary    = C 0x4D 0xD0 0xA7
$OnPrimary  = C 0x07 0x23 0x1A
$Monitoring = C 0x4D 0xD0 0xA7
$Possible   = C 0xFF 0xC8 0x57
$Confirmed  = C 0xFF 0x8A 0x3D
$Barking    = C 0xFF 0x52 0x52
$Cooldown   = C 0x7F 0xB6 0xFF
$Idle       = C 0x8E 0x9A 0xAB
$White      = [System.Drawing.Color]::White
# Built from code points rather than written literally: PowerShell 5.1 reads this
# file under the ANSI code page, which mangles any non-ASCII character in source.
# U+1F431 is also outside the BMP, so it needs a surrogate pair.
$script:CAT_EMOJI = [char]::ConvertFromUtf32(0x1F431)
$script:DOT = [string][char]0x00B7
$script:BULLET = [string][char]0x2022

$FONT = 'Segoe UI'
function Fnt([double]$sp, [string]$style = 'Regular') {
    # Compose sp maps to the same scale as dp here; 0.75 converts px to GDI points.
    New-Object System.Drawing.Font($FONT, [float]($sp * $script:SCALE_PX * 0.75), [System.Drawing.FontStyle]$style)
}

# --- drawing helpers ---------------------------------------------------------
function New-Screen {
    $bmp = New-Object System.Drawing.Bitmap($script:SCREEN_W, $script:SCREEN_H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear($Ink)
    return @($bmp, $g)
}

function RoundRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function Fill-RoundRect($g, [float]$x, [float]$y, [float]$w, [float]$h, [float]$r, $color) {
    $p = RoundRectPath $x $y $w $h $r
    $b = New-Object System.Drawing.SolidBrush($color)
    $g.FillPath($b, $p); $b.Dispose(); $p.Dispose()
}

function Stroke-RoundRect($g, [float]$x, [float]$y, [float]$w, [float]$h, [float]$r, $color, [float]$width) {
    $p = RoundRectPath $x $y $w $h $r
    $pen = New-Object System.Drawing.Pen($color, $width)
    $g.DrawPath($pen, $p); $pen.Dispose(); $p.Dispose()
}

function Txt($g, [string]$s, $font, $color, [float]$x, [float]$y) {
    $b = New-Object System.Drawing.SolidBrush($color)
    $fmt = [System.Drawing.StringFormat]::GenericTypographic
    $g.DrawString($s, $font, $b, $x, $y, $fmt)
    $b.Dispose()
}

function TxtW($g, [string]$s, $font) {
    $g.MeasureString($s, $font, [System.Drawing.PointF]::Empty,
        [System.Drawing.StringFormat]::GenericTypographic).Width
}

function TxtRight($g, [string]$s, $font, $color, [float]$right, [float]$y) {
    Txt $g $s $font $color ($right - (TxtW $g $s $font)) $y
}

# Letter-spaced text, for the "CAT GUARD" wordmark.
function TxtTracked($g, [string]$s, $font, $color, [float]$x, [float]$y, [float]$track) {
    $cx = $x
    foreach ($ch in $s.ToCharArray()) {
        Txt $g ([string]$ch) $font $color $cx $y
        $cx += (TxtW $g ([string]$ch) $font) + $track
    }
}

function Draw-StatusBar($g) {
    $f = Fnt 11 'Regular'
    Txt $g '9:41' $f $OnVar (dp 16) (dp 6)
    # signal / wifi / battery, sketched rather than iconographic
    $b = New-Object System.Drawing.SolidBrush($OnVar)
    for ($i = 0; $i -lt 4; $i++) {
        $hgt = dp (3 + $i * 1.6)
        $g.FillRectangle($b, (dp (300 + $i * 5)), (dp 15) - $hgt, (dp 3), $hgt)
    }
    $g.FillRectangle($b, (dp 326), (dp 8), (dp 16), (dp 8))
    $b.Dispose(); $f.Dispose()
}

# A cat seen side-on, for the mocked camera view.
function Draw-CatBody($g, [float]$cx, [float]$cy, [float]$s, $color) {
    $b = New-Object System.Drawing.SolidBrush($color)
    $g.FillEllipse($b, $cx - 34 * $s, $cy - 14 * $s, 62 * $s, 30 * $s)          # body
    $g.FillEllipse($b, $cx + 16 * $s, $cy - 30 * $s, 24 * $s, 22 * $s)         # head
    $ear1 = @(
        (New-Object System.Drawing.PointF(($cx + 19 * $s), ($cy - 27 * $s))),
        (New-Object System.Drawing.PointF(($cx + 21 * $s), ($cy - 38 * $s))),
        (New-Object System.Drawing.PointF(($cx + 28 * $s), ($cy - 28 * $s))))
    $ear2 = @(
        (New-Object System.Drawing.PointF(($cx + 31 * $s), ($cy - 28 * $s))),
        (New-Object System.Drawing.PointF(($cx + 37 * $s), ($cy - 37 * $s))),
        (New-Object System.Drawing.PointF(($cx + 39 * $s), ($cy - 25 * $s))))
    $g.FillPolygon($b, $ear1); $g.FillPolygon($b, $ear2)
    $g.FillRectangle($b, $cx - 26 * $s, $cy + 10 * $s, 7 * $s, 18 * $s)        # legs
    $g.FillRectangle($b, $cx - 6 * $s, $cy + 10 * $s, 7 * $s, 18 * $s)
    $g.FillRectangle($b, $cx + 14 * $s, $cy + 10 * $s, 7 * $s, 18 * $s)
    $penW = [float](5 * $s)
    $pen = New-Object System.Drawing.Pen($color, $penW)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawBezier($pen,
        (New-Object System.Drawing.PointF(($cx - 33 * $s), ($cy - 4 * $s))),
        (New-Object System.Drawing.PointF(($cx - 52 * $s), ($cy - 10 * $s))),
        (New-Object System.Drawing.PointF(($cx - 50 * $s), ($cy - 34 * $s))),
        (New-Object System.Drawing.PointF(($cx - 38 * $s), ($cy - 36 * $s))))
    $pen.Dispose(); $b.Dispose()
}

# The mocked camera preview: a dim room with a lit doorway.
function Draw-Preview($g, [float]$x, [float]$y, [float]$w, [float]$h, [bool]$withCat,
                      $boxColor, [string]$boxLabel, [bool]$dimOutsideZone) {
    $clip = RoundRectPath $x $y $w $h (dp 14)
    $g.SetClip($clip)

    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.PointF($x, $y)),
        (New-Object System.Drawing.PointF($x, ($y + $h))),
        (C 0x12 0x18 0x22), (C 0x1E 0x26 0x30))
    $g.FillRectangle($bg, $x, $y, $w, $h); $bg.Dispose()

    # floor
    $floor = New-Object System.Drawing.SolidBrush((C 0x23 0x2C 0x38))
    $g.FillRectangle($floor, $x, ($y + $h * 0.52), $w, ($h * 0.48)); $floor.Dispose()

    # doorway with warm light spilling out
    $door = New-Object System.Drawing.SolidBrush((C 0x0C 0x11 0x18))
    $g.FillRectangle($door, ($x + $w * 0.62), ($y + $h * 0.10), ($w * 0.26), ($h * 0.46))
    $door.Dispose()
    $glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glowPath.AddEllipse(($x + $w * 0.50), ($y + $h * 0.40), ($w * 0.52), ($h * 0.38))
    $pg = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath)
    $pg.CenterColor = C 0xFF 0xD9 0x9A 46
    $pg.SurroundColors = @((C 0xFF 0xD9 0x9A 0))
    $g.FillPath($pg, $glowPath); $pg.Dispose(); $glowPath.Dispose()

    # floor tile lines
    $pen = New-Object System.Drawing.Pen((C 0xFF 0xFF 0xFF 14), 2)
    for ($i = 1; $i -lt 6; $i++) {
        $ly = $y + $h * 0.52 + ($h * 0.48) * ($i / 6.0)
        $g.DrawLine($pen, $x, $ly, ($x + $w), $ly)
    }
    $pen.Dispose()

    if ($dimOutsideZone) {
        # Detection zone: everything outside it is dimmed, as the overlay does.
        $zx = $x + $w * 0.08; $zy = $y + $h * 0.42
        $zw = $w * 0.84;      $zh = $h * 0.50
        $dim = New-Object System.Drawing.SolidBrush((C 0 0 0 115))
        $g.FillRectangle($dim, $x, $y, $w, ($zy - $y))
        $g.FillRectangle($dim, $x, ($zy + $zh), $w, ($y + $h - $zy - $zh))
        $g.FillRectangle($dim, $x, $zy, ($zx - $x), $zh)
        $g.FillRectangle($dim, ($zx + $zw), $zy, ($x + $w - $zx - $zw), $zh)
        $dim.Dispose()
        $zpen = New-Object System.Drawing.Pen($Cooldown, (dp 1.5))
        $g.DrawRectangle($zpen, $zx, $zy, $zw, $zh); $zpen.Dispose()
    }

    if ($withCat) {
        $cs = $w / 300.0
        $ccx = $x + $w * 0.40
        $ccy = $y + $h * 0.70
        Draw-CatBody $g $ccx $ccy $cs (C 0x2A 0x2F 0x38)

        # detection box + label, drawn the way DetectionOverlay does
        $bx = $ccx - 62 * $cs; $by = $ccy - 46 * $cs
        $bw = 118 * $cs;       $bh = 82 * $cs
        $bp = New-Object System.Drawing.Pen($boxColor, (dp 3))
        $g.DrawRectangle($bp, $bx, $by, $bw, $bh); $bp.Dispose()

        $lf = Fnt 15 'Bold'
        $lw = (TxtW $g $boxLabel $lf) + (dp 8)
        $lh = (dp 20)
        $lb = New-Object System.Drawing.SolidBrush(
            [System.Drawing.Color]::FromArgb(217, $boxColor.R, $boxColor.G, $boxColor.B))
        $g.FillRectangle($lb, $bx, ($by - $lh), $lw, $lh); $lb.Dispose()
        Txt $g $boxLabel $lf $Ink ($bx + (dp 4)) ($by - $lh + (dp 2))
        $lf.Dispose()
    }

    $g.ResetClip()
    Stroke-RoundRect $g $x $y $w $h (dp 14) (C 0xFF 0xFF 0xFF 18) 2
    $clip.Dispose()
}

# --- main screen -------------------------------------------------------------
function Render-Main([string]$file, [string]$stateLabel, $stateColor, [string]$detail,
                     [bool]$withCat, [string]$boxLabel, $boxColor,
                     [int]$today, [int]$barks, [bool]$running, [string]$extraLine, $extraColor) {
    $r = New-Screen; $bmp = $r[0]; $g = $r[1]
    Draw-StatusBar $g

    # header
    $tf = Fnt 22 'Bold'
    TxtTracked $g 'CAT GUARD' $tf $OnBg (dp 16) (dp 36) (dp 3)
    $tf.Dispose()

    # Note the parentheses around every `dp` call: `dp 344 - $sw` would be parsed
    # as passing three arguments to dp, not as arithmetic.
    $bf = Fnt 14 'Regular'
    $settingsW = TxtW $g 'Settings' $bf
    TxtRight $g 'Settings' $bf $Primary (dp 344) (dp 40)
    TxtRight $g 'History' $bf $Primary ((dp 344) - $settingsW - (dp 14)) (dp 40)
    $bf.Dispose()

    # Today's totals, laid out left to right. Widths are measured up front rather
    # than returned from the draw helper, because a PowerShell function returns
    # everything its body emits, not just the value after `return`.
    $vf = Fnt 26 'Bold'
    $lf = Fnt 12 'Regular'
    $base = dp 92
    $silent = $today - $barks

    $stats = @(
        @("$today", 'cats today', $Confirmed),
        @("$barks", 'barks today', $Barking)
    )
    if ($silent -gt 0) { $stats += , @("$silent", 'silent', $Idle) }

    $sx = dp 16
    foreach ($stat in $stats) {
        $vw = TxtW $g $stat[0] $vf
        Txt $g $stat[0] $vf $stat[2] $sx ($base - (dp 27))
        Txt $g $stat[1] $lf $OnVar ($sx + $vw + (dp 5)) ($base - (dp 14))
        $sx += $vw + (dp 5) + (TxtW $g $stat[1] $lf) + (dp 18)
    }
    $vf.Dispose(); $lf.Dispose()

    # preview
    $px = dp 12; $py = dp 106
    $pw = dp 336; $ph = dp 372
    Draw-Preview $g $px $py $pw $ph $withCat $boxColor $boxLabel $true

    # status card
    $cx = dp 12; $cy = dp 490
    $cw = dp 336
    $ch = if ($extraLine) { dp 116 } else { dp 92 }
    Fill-RoundRect $g $cx $cy $cw $ch (dp 14) $Surface

    $dot = New-Object System.Drawing.SolidBrush($stateColor)
    $g.FillEllipse($dot, ($cx + (dp 16)), ($cy + (dp 18)), (dp 12), (dp 12)); $dot.Dispose()
    $sf = Fnt 19 'Bold'
    Txt $g $stateLabel $sf $stateColor ($cx + (dp 38)) ($cy + (dp 13))
    $sf.Dispose()

    $df = Fnt 12 'Regular'
    Txt $g $detail $df $OnVar ($cx + (dp 16)) ($cy + (dp 44))
    $df.Dispose()
    $wf = Fnt 11 'Regular'
    Txt $g 'Watching for cat' $wf $OnVar ($cx + (dp 16)) ($cy + (dp 62))
    $wf.Dispose()
    if ($extraLine) {
        $ef = Fnt 12 'Regular'
        Txt $g $extraLine $ef $extraColor ($cx + (dp 16)) ($cy + (dp 84))
        $ef.Dispose()
    }

    # controls
    $by = dp 622
    $bw2 = dp 268
    if ($running) {
        Fill-RoundRect $g (dp 12) $by $bw2 (dp 54) (dp 12) $Barking
        $bf2 = Fnt 15 'Bold'
        $t = 'STOP GUARD'
        Txt $g $t $bf2 $White ((dp 12) + ($bw2 - (TxtW $g $t $bf2)) / 2) ($by + (dp 16))
        $bf2.Dispose()
    } else {
        Fill-RoundRect $g (dp 12) $by $bw2 (dp 54) (dp 12) $Primary
        $bf2 = Fnt 15 'Bold'
        $t = 'START GUARD'
        Txt $g $t $bf2 $OnPrimary ((dp 12) + ($bw2 - (TxtW $g $t $bf2)) / 2) ($by + (dp 16))
        $bf2.Dispose()
    }
    Stroke-RoundRect $g (dp 290) $by (dp 58) (dp 54) (dp 12) $Primary 2
    $zf = Fnt 14 'Regular'
    Txt $g 'ZONE' $zf $Primary ((dp 290) + ((dp 58) - (TxtW $g 'ZONE' $zf)) / 2) ($by + (dp 17))
    $zf.Dispose()

    $bmp.Save((Join-Path $out $file), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# --- settings: deterrent sound ----------------------------------------------
function Render-Sound([string]$file) {
    $r = New-Screen; $bmp = $r[0]; $g = $r[1]
    Draw-StatusBar $g

    $tf = Fnt 22 'Bold'
    Txt $g 'Settings' $tf $OnBg (dp 16) (dp 34)
    $tf.Dispose()
    $bf = Fnt 14 'Regular'
    TxtRight $g 'Done' $bf $Primary (dp 344) (dp 40)
    $bf.Dispose()

    $cx = dp 12; $cy = dp 74; $cw = dp 336; $ch = dp 620
    Fill-RoundRect $g $cx $cy $cw $ch (dp 14) $Surface

    $hf = Fnt 12 'Bold'
    TxtTracked $g 'DETERRENT SOUND' $hf $Primary ($cx + (dp 16)) ($cy + (dp 14)) (dp 1.5)
    $hf.Dispose()

    # loud button
    Fill-RoundRect $g ($cx + (dp 16)) ($cy + (dp 40)) ($cw - (dp 32)) (dp 40) (dp 20) $Barking
    $lf = Fnt 14 'Bold'
    $t = 'MAKE IT AS LOUD AS POSSIBLE'
    Txt $g $t $lf $White ($cx + (dp 16) + (($cw - (dp 32)) - (TxtW $g $t $lf)) / 2) ($cy + (dp 51))
    $lf.Dispose()

    # combinations
    $sf = Fnt 13 'Bold'
    Txt $g 'Combinations' $sf $OnBg ($cx + (dp 16)) ($cy + (dp 92))
    $sf.Dispose()

    $combos = @(
        @('Just one bark',      'Single bark',                          ('in turn  ' + $script:BULLET + '  4s'),  $false),
        @('Angry dog',          'Alert barking, Sustained barking',     ('together  ' + $script:BULLET + '  12s'), $false),
        @('Startling noises',   'Vacuum cleaner, Thunder, Fireworks',   ('together  ' + $script:BULLET + '  20s'), $true),
        @('Everything at once', 'Single bark, Double bark and 5 more',  ('together  ' + $script:BULLET + '  30s'), $false)
    )
    $y = $cy + (dp 114)
    foreach ($c in $combos) {
        $rowH = dp 46
        $active = $c[3]
        $bgc = if ($active) { C 0x4D 0xD0 0xA7 36 } else { C 0x24 0x30 0x40 102 }
        Fill-RoundRect $g ($cx + (dp 16)) $y ($cw - (dp 32)) $rowH (dp 10) $bgc
        if ($active) { Stroke-RoundRect $g ($cx + (dp 16)) $y ($cw - (dp 32)) $rowH (dp 10) $Primary 2 }

        $nf = Fnt 14 'Bold'
        $nameColor = if ($active) { $Primary } else { $OnBg }
        Txt $g $c[0] $nf $nameColor ($cx + (dp 28)) ($y + (dp 6))
        if ($active) {
            $tagf = Fnt 9 'Regular'
            Txt $g 'IN USE' $tagf $Primary ($cx + (dp 28) + (TxtW $g $c[0] $nf) + (dp 8)) ($y + (dp 10))
            $tagf.Dispose()
        }
        $nf.Dispose()
        $df = Fnt 10 'Regular'
        Txt $g $c[1] $df $OnVar ($cx + (dp 28)) ($y + (dp 22))
        Txt $g $c[2] $df $OnVar ($cx + (dp 28)) ($y + (dp 33))
        $df.Dispose()
        $y += $rowH + (dp 6)
    }

    # tick list
    $y += dp 8
    $sf = Fnt 13 'Bold'
    Txt $g 'Tick everything you want to play' $sf $OnBg ($cx + (dp 16)) $y
    $sf.Dispose()
    $y += dp 18
    $df = Fnt 11 'Regular'
    Txt $g '3 selected. More than one is louder and harder for an' $df $OnVar ($cx + (dp 16)) $y
    Txt $g 'animal to get used to.' $df $OnVar ($cx + (dp 16)) ($y + (dp 13))
    $df.Dispose()
    $y += dp 34

    $sounds = @(
        @('DOG BARKS', $null, $false),
        @('Single bark',       ('One sharp bark.  ' + $script:BULLET + '  2s clip'),  $false),
        @('Double bark',       ('Two close barks.  ' + $script:BULLET + '  3s clip'), $false),
        @('Alert barking',     ('An agitated burst.  ' + $script:BULLET + '  4s clip'), $false),
        @('Sustained barking', ('Continuous angry barking.  ' + $script:BULLET + '  12s clip'), $false),
        @('STARTLING NOISES', $null, $false),
        @('Vacuum cleaner',    ('Loud continuous motor whine.  ' + $script:BULLET + '  12s clip'), $true),
        @('Thunder',           ('Thunderclaps and rumble.  ' + $script:BULLET + '  14s clip'), $true),
        @('Fireworks',         ('Bangs and crackles.  ' + $script:BULLET + '  15s clip'), $true)
    )
    foreach ($s in $sounds) {
        if ($null -eq $s[1]) {
            $gf = Fnt 10 'Bold'
            TxtTracked $g $s[0] $gf $Primary ($cx + (dp 16)) ($y + (dp 4)) (dp 1)
            $gf.Dispose()
            $y += dp 20
            continue
        }
        $checked = $s[2]
        # checkbox
        if ($checked) {
            Fill-RoundRect $g ($cx + (dp 18)) ($y + (dp 3)) (dp 16) (dp 16) (dp 3) $Primary
            $pen = New-Object System.Drawing.Pen($Ink, 3)
            $g.DrawLines($pen, @(
                (New-Object System.Drawing.PointF(($cx + (dp 22)), ($y + (dp 11)))),
                (New-Object System.Drawing.PointF(($cx + (dp 25)), ($y + (dp 15)))),
                (New-Object System.Drawing.PointF(($cx + (dp 30)), ($y + (dp 7))))))
            $pen.Dispose()
        } else {
            Stroke-RoundRect $g ($cx + (dp 18)) ($y + (dp 3)) (dp 16) (dp 16) (dp 3) $OnVar 2
        }
        $nf = Fnt 14 ($(if ($checked) { 'Bold' } else { 'Regular' }))
        Txt $g $s[0] $nf $OnBg ($cx + (dp 44)) ($y + (dp 1))
        $nf.Dispose()
        $df = Fnt 10 'Regular'
        Txt $g $s[1] $df $OnVar ($cx + (dp 44)) ($y + (dp 17))
        $df.Dispose()
        $y += dp 32
    }

    $bmp.Save((Join-Path $out $file), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# --- history -----------------------------------------------------------------
function Render-History([string]$file) {
    $r = New-Screen; $bmp = $r[0]; $g = $r[1]
    Draw-StatusBar $g

    $tf = Fnt 22 'Bold'
    Txt $g 'History' $tf $OnBg (dp 16) (dp 34)
    $tf.Dispose()
    $sf = Fnt 12 'Regular'
    Txt $g '23 confirmed encounters, last 14 days' $sf $OnVar (dp 16) (dp 64)
    $sf.Dispose()
    $bf = Fnt 14 'Regular'
    TxtRight $g 'Done' $bf $Primary (dp 344) (dp 40)
    $bf.Dispose()

    # day-by-day card
    $cx = dp 12; $cy = dp 90; $cw = dp 336; $ch = dp 236
    Fill-RoundRect $g $cx $cy $cw $ch (dp 14) $Surface
    $hf = Fnt 11 'Bold'
    TxtTracked $g 'DAY BY DAY' $hf $Primary ($cx + (dp 16)) ($cy + (dp 14)) (dp 1.5)
    $hf.Dispose()

    $colf = Fnt 11 'Regular'
    Txt $g 'Day' $colf $OnVar ($cx + (dp 16)) ($cy + (dp 38))
    Txt $g 'Detected' $colf $OnVar ($cx + (dp 190)) ($cy + (dp 38))
    Txt $g 'Barked' $colf $OnVar ($cx + (dp 268)) ($cy + (dp 38))
    $colf.Dispose()
    $pen = New-Object System.Drawing.Pen((C 0xFF 0xFF 0xFF 28), 2)
    $g.DrawLine($pen, ($cx + (dp 16)), ($cy + (dp 56)), ($cx + $cw - (dp 16)), ($cy + (dp 56)))

    $rows = @(
        @('Today', '4', '3'),
        @('Yesterday', '6', '5'),
        @('Mon 15 Sep', '2', '2'),
        @('Sun 14 Sep', '7', '6'),
        @('Sat 13 Sep', '4', '3')
    )
    $y = $cy + (dp 64)
    foreach ($row in $rows) {
        $nf = Fnt 14 'Bold'
        Txt $g $row[0] $nf $OnBg ($cx + (dp 16)) $y
        $nf.Dispose()
        $vf = Fnt 15 'Bold'
        Txt $g $row[1] $vf $Confirmed ($cx + (dp 190)) $y
        Txt $g $row[2] $vf $Barking ($cx + (dp 268)) $y
        $vf.Dispose()
        $y += dp 28
    }
    $g.DrawLine($pen, ($cx + (dp 16)), ($y + (dp 2)), ($cx + $cw - (dp 16)), ($y + (dp 2)))
    $pen.Dispose()
    $tfb = Fnt 13 'Bold'
    Txt $g 'Total' $tfb $OnBg ($cx + (dp 16)) ($y + (dp 10))
    Txt $g '23' $tfb $OnBg ($cx + (dp 190)) ($y + (dp 10))
    Txt $g '19' $tfb $OnBg ($cx + (dp 268)) ($y + (dp 10))
    $tfb.Dispose()

    # every encounter
    $hf = Fnt 11 'Bold'
    TxtTracked $g 'EVERY ENCOUNTER' $hf $Primary (dp 16) (dp 340) (dp 1.5)
    $hf.Dispose()

    $events = @(
        @($CAT_EMOJI, '02:13 AM', 'Today',     'Cat  91%', 'Bark triggered', $true),
        @($CAT_EMOJI, '01:48 AM', 'Today',     'Cat  84%', 'Bark triggered', $true),
        @($CAT_EMOJI, '11:56 PM', 'Yesterday', 'Cat  78%', 'No sound',       $false),
        @($CAT_EMOJI, '10:02 PM', 'Yesterday', 'Cat  88%', 'Bark triggered', $true),
        @($CAT_EMOJI, '08:31 PM', 'Yesterday', 'Cat  73%', 'Bark triggered', $true),
        @($CAT_EMOJI, '06:12 PM', 'Yesterday', 'Cat  95%', 'Bark triggered', $true)
    )
    $y = dp 362
    foreach ($e in $events) {
        Fill-RoundRect $g (dp 12) $y (dp 336) (dp 52) (dp 10) $Surface
        $ef = New-Object System.Drawing.Font('Segoe UI Emoji', [float](18 * $script:SCALE_PX * 0.75))
        Txt $g ([string]$e[0]) $ef $White (dp 26) ($y + (dp 14))
        $ef.Dispose()
        $nf = Fnt 15 'Bold'
        Txt $g $e[1] $nf $OnBg (dp 66) ($y + (dp 9))
        $nf.Dispose()
        $df = Fnt 11 'Regular'
        Txt $g $e[2] $df $OnVar (dp 66) ($y + (dp 30))
        $df.Dispose()
        $vf = Fnt 14 'Regular'
        TxtRight $g $e[3] $vf $OnBg (dp 336) ($y + (dp 9))
        $vf.Dispose()
        $rf = Fnt 11 'Regular'
        $rc = if ($e[5]) { $Barking } else { $OnVar }
        TxtRight $g $e[4] $rf $rc (dp 336) ($y + (dp 30))
        $rf.Dispose()
        $y += dp 58
    }

    $bmp.Save((Join-Path $out $file), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# --- render ------------------------------------------------------------------
Render-Main '1-monitoring.png' 'MONITORING' $Monitoring `
    ("Confirm 0/3 frames  " + $script:DOT + "  55% threshold  " + $script:DOT + "  74 ms/frame") `
    $false '' $Monitoring 4 3 $true '' $OnVar

Render-Main '2-cat-detected.png' 'CAT CONFIRMED' $Confirmed `
    ("Confirm 2/3 frames  " + $script:DOT + "  55% threshold  " + $script:DOT + "  81 ms/frame") `
    $true 'CAT 91%' $Confirmed 5 3 $true '' $OnVar

Render-Main '3-barking.png' 'BARKING' $Barking `
    ("Confirm 2/3 frames  " + $script:DOT + "  55% threshold  " + $script:DOT + "  79 ms/frame") `
    $true 'CAT 91%' $Barking 5 4 $true '' $OnVar

Render-Main '4-cooldown.png' 'COOLDOWN - 14s' $Cooldown `
    ("Confirm 1/3 frames  " + $script:DOT + "  55% threshold  " + $script:DOT + "  76 ms/frame") `
    $true 'CAT 86%' $Cooldown 5 4 $true `
    'Scene is dark - detection is unreliable.' $Possible

Render-Sound '5-sounds.png'
Render-History '6-history.png'

Get-ChildItem $out -Filter *.png | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    "{0,-22} {1}x{2}  {3} KB" -f $_.Name, $img.Width, $img.Height, [math]::Round($_.Length / 1KB)
    $img.Dispose()
}
