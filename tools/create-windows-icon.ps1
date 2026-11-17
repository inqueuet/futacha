param([Parameter(Mandatory=$true)][string]$Source, [Parameter(Mandatory=$true)][string]$Target)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$original = [System.Drawing.Image]::FromFile($Source)
$bitmap = New-Object System.Drawing.Bitmap(256, 256)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$png = New-Object System.IO.MemoryStream
try {
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($original, 0, 0, 256, 256)
    $bitmap.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
    $writer = New-Object System.IO.BinaryWriter([System.IO.File]::Create($Target))
    try {
        $writer.Write([UInt16]0); $writer.Write([UInt16]1); $writer.Write([UInt16]1)
        $writer.Write([UInt32]0); $writer.Write([UInt16]1); $writer.Write([UInt16]32)
        $writer.Write([UInt32]$png.Length); $writer.Write([UInt32]22)
        $writer.Write($png.ToArray())
    } finally { $writer.Dispose() }
} finally { $graphics.Dispose(); $bitmap.Dispose(); $original.Dispose(); $png.Dispose() }
