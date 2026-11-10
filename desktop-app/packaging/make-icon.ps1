# Draws the MIYAV mark (paw print on a warm orange squircle with a cream sparkle; same geometry as
# android-app/src/main/res/drawable/ic_mark.xml) and writes miyav.ico plus a 256 px PNG.
param(
    [string]$IcoPath = "$PSScriptRoot\miyav.ico",
    [string]$PngPath = "$PSScriptRoot\..\src\main\resources\az\miyav\desktop\miyav-icon.png"
)
Add-Type -AssemblyName System.Drawing

function New-Mark([int]$size) {
    $bitmap = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.Clear([System.Drawing.Color]::Transparent)
    $k = $size / 100.0
    $g.ScaleTransform($k, $k)

    $r = 28.0
    $square = New-Object System.Drawing.Drawing2D.GraphicsPath
    $square.AddArc(0, 0, 2 * $r, 2 * $r, 180, 90)
    $square.AddArc(100 - 2 * $r, 0, 2 * $r, 2 * $r, 270, 90)
    $square.AddArc(100 - 2 * $r, 100 - 2 * $r, 2 * $r, 2 * $r, 0, 90)
    $square.AddArc(0, 100 - 2 * $r, 2 * $r, 2 * $r, 90, 90)
    $square.CloseFigure()
    $fill = New-Object System.Drawing.Drawing2D.LinearGradientBrush ((New-Object System.Drawing.PointF 0, 0)), ((New-Object System.Drawing.PointF 100, 100)), ([System.Drawing.Color]::FromArgb(255, 0xF3, 0x9A, 0x4E)), ([System.Drawing.Color]::FromArgb(255, 0xA9, 0x46, 0x0F))
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend 3
    $blend.Colors = @([System.Drawing.Color]::FromArgb(255, 0xF3, 0x9A, 0x4E), [System.Drawing.Color]::FromArgb(255, 0xDE, 0x6A, 0x1F), [System.Drawing.Color]::FromArgb(255, 0xA9, 0x46, 0x0F))
    $blend.Positions = @(0.0, 0.55, 1.0)
    $fill.InterpolationColors = $blend
    $g.FillPath($fill, $square)

    $state = $g.Save()
    $g.TranslateTransform(50, 52)
    $g.ScaleTransform(0.8, 0.8)
    $g.TranslateTransform(-50, -53)
    $white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $pad = New-Object System.Drawing.Drawing2D.GraphicsPath
    $pad.AddBezier(50, 50, 41, 50, 31, 60, 27.5, 68.5)
    $pad.AddBezier(27.5, 68.5, 24.8, 75.2, 29, 82, 36.5, 82)
    $pad.AddBezier(36.5, 82, 42, 82, 45, 79.5, 50, 79.5)
    $pad.AddBezier(50, 79.5, 55, 79.5, 58, 82, 63.5, 82)
    $pad.AddBezier(63.5, 82, 71, 82, 75.2, 75.2, 72.5, 68.5)
    $pad.AddBezier(72.5, 68.5, 69, 60, 59, 50, 50, 50)
    $pad.CloseFigure()
    $g.FillPath($white, $pad)
    $g.FillEllipse($white, 20.8, 37.8, 14.4, 14.4)
    $g.FillEllipse($white, 32.5, 24, 16, 16)
    $g.FillEllipse($white, 51.5, 24, 16, 16)
    $g.FillEllipse($white, 64.8, 37.8, 14.4, 14.4)
    $g.Restore($state)

    $gold = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 0xFF, 0xE7, 0xB8))
    $star = [System.Drawing.PointF[]]@(
        (New-Object System.Drawing.PointF 81, 11), (New-Object System.Drawing.PointF 82.8, 16.2),
        (New-Object System.Drawing.PointF 88, 18), (New-Object System.Drawing.PointF 82.8, 19.8),
        (New-Object System.Drawing.PointF 81, 25), (New-Object System.Drawing.PointF 79.2, 19.8),
        (New-Object System.Drawing.PointF 74, 18), (New-Object System.Drawing.PointF 79.2, 16.2))
    $g.FillPolygon($gold, $star)
    $g.Dispose()
    return $bitmap
}

function Get-PngBytes($bitmap) {
    $stream = New-Object System.IO.MemoryStream
    $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
    return $stream.ToArray()
}

$sizes = @(256, 64, 48, 32, 16)
$images = New-Object 'System.Collections.Generic.List[byte[]]'
foreach ($s in $sizes) { $images.Add([byte[]](Get-PngBytes (New-Mark $s))) }

# ICO container with PNG-compressed entries (supported since Windows Vista).
$out = New-Object System.IO.MemoryStream
$writer = New-Object System.IO.BinaryWriter $out
$writer.Write([UInt16]0); $writer.Write([UInt16]1); $writer.Write([UInt16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $dim = if ($sizes[$i] -ge 256) { 0 } else { $sizes[$i] }
    $writer.Write([byte]$dim); $writer.Write([byte]$dim); $writer.Write([byte]0); $writer.Write([byte]0)
    $writer.Write([UInt16]1); $writer.Write([UInt16]32)
    $writer.Write([UInt32]$images[$i].Length); $writer.Write([UInt32]$offset)
    $offset += $images[$i].Length
}
foreach ($bytes in $images) { $writer.Write($bytes) }
$writer.Flush()
[System.IO.File]::WriteAllBytes($IcoPath, $out.ToArray())

New-Item -ItemType Directory -Force -Path (Split-Path $PngPath) | Out-Null
[System.IO.File]::WriteAllBytes($PngPath, $images[0])
"Wrote $IcoPath and $PngPath"
