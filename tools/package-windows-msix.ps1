param(
    [Parameter(Mandatory=$true)][string]$AppImage,
    [Parameter(Mandatory=$true)][string]$ManifestTemplate,
    [Parameter(Mandatory=$true)][string]$SourceIcon,
    [Parameter(Mandatory=$true)][string]$StagingDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [Parameter(Mandatory=$true)][string]$Version,
    [string]$MakeAppxPath = "",
    [switch]$StageOnly,
    [switch]$PackageOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Drawing

if ($StageOnly -and $PackageOnly) {
    throw 'StageOnly and PackageOnly cannot be used together.'
}

$parts = $Version.Split('.')
$invalidParts = @($parts | Where-Object { $_ -notmatch '^\d+$' })
if ($parts.Count -ne 4 -or $invalidParts.Count -gt 0) {
    throw "MSIX version must contain four numeric parts: $Version"
}
$numbers = @($parts | ForEach-Object { [int]$_ })
$oversizedParts = @($numbers | Where-Object { $_ -gt 65535 })
if ($numbers[0] -eq 0 -or $oversizedParts.Count -gt 0) {
    throw "MSIX version parts must be 0-65535 and the first part cannot be zero: $Version"
}
if ($numbers[3] -ne 0) {
    throw "The fourth MSIX version part is reserved by Microsoft Store and must be zero: $Version"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$resolvedStage = [System.IO.Path]::GetFullPath($StagingDirectory)
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)

function Write-ScaledPng([System.Drawing.Image]$Image, [int]$Size, [string]$Path) {
    $bitmap = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.DrawImage($Image, 0, 0, $Size, $Size)
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Stage-Package {
    foreach ($path in @($AppImage, $ManifestTemplate, $SourceIcon)) {
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Required MSIX input does not exist: $path"
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $AppImage 'Futacha.exe'))) {
        throw "The release app image does not contain Futacha.exe: $AppImage"
    }

    if (Test-Path -LiteralPath $resolvedStage) {
        Remove-Item -LiteralPath $resolvedStage -Recurse -Force
    }
    New-Item -ItemType Directory -Path $resolvedStage | Out-Null
    Get-ChildItem -LiteralPath $AppImage -Force | Copy-Item -Destination $resolvedStage -Recurse -Force

    $assets = Join-Path $resolvedStage 'Assets'
    New-Item -ItemType Directory -Path $assets | Out-Null
    $icon = [System.Drawing.Image]::FromFile([System.IO.Path]::GetFullPath($SourceIcon))
    try {
        Write-ScaledPng $icon 50 (Join-Path $assets 'StoreLogo.png')
        Write-ScaledPng $icon 150 (Join-Path $assets 'Square150x150Logo.png')
        Write-ScaledPng $icon 44 (Join-Path $assets 'Square44x44Logo.png')
    } finally {
        $icon.Dispose()
    }

    $manifest = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($ManifestTemplate), [System.Text.Encoding]::UTF8)
    if (-not $manifest.Contains('@MSIX_VERSION@')) {
        throw 'The MSIX manifest template does not contain @MSIX_VERSION@.'
    }
    $manifest = $manifest.Replace('@MSIX_VERSION@', $Version)
    [System.IO.File]::WriteAllText((Join-Path $resolvedStage 'AppxManifest.xml'), $manifest, $utf8NoBom)

    $nonAscii = Get-ChildItem -LiteralPath $resolvedStage -Recurse -Force | Where-Object {
        $_.FullName.Substring($resolvedStage.Length).ToCharArray() | Where-Object { [int]$_ -gt 127 }
    } | Select-Object -First 1
    if ($null -ne $nonAscii) {
        throw "Microsoft Store package paths must use ASCII filenames: $($nonAscii.FullName)"
    }
    Write-Host "Staged Microsoft Store package: $resolvedStage"
}

function Resolve-MakeAppx {
    if ($MakeAppxPath) {
        if (-not (Test-Path -LiteralPath $MakeAppxPath -PathType Leaf)) {
            throw "MakeAppx.exe was not found at the requested path: $MakeAppxPath"
        }
        return [System.IO.Path]::GetFullPath($MakeAppxPath)
    }
    $command = Get-Command MakeAppx.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }
    $programFilesX86 = [Environment]::GetFolderPath('ProgramFilesX86')
    $kits = Join-Path $programFilesX86 'Windows Kits\10\bin'
    $candidates = @(Get-ChildItem -Path (Join-Path $kits '*\x64\MakeAppx.exe') -File -ErrorAction SilentlyContinue |
        Sort-Object { try { [Version]$_.Directory.Parent.Name } catch { [Version]'0.0' } } -Descending)
    if ($candidates.Count -eq 0) {
        throw 'MakeAppx.exe was not found. Install the Windows 10/11 SDK, then rerun packageWindowsStoreMsix.'
    }
    return $candidates[0].FullName
}

if (-not $PackageOnly) {
    Stage-Package
}
if ($StageOnly) {
    exit 0
}
if (-not (Test-Path -LiteralPath (Join-Path $resolvedStage 'AppxManifest.xml'))) {
    throw "The staged package is missing AppxManifest.xml: $resolvedStage"
}

New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
$package = Join-Path $resolvedOutput "Futacha-$Version-windows-x64.msix"
$hashFile = "$package.sha256"
foreach ($path in @($package, $hashFile)) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Force
    }
}

$makeAppx = Resolve-MakeAppx
& $makeAppx pack /o /h SHA256 /d $resolvedStage /p $package
if ($LASTEXITCODE -ne 0) {
    throw "MakeAppx.exe failed with exit code $LASTEXITCODE"
}
$stream = [System.IO.File]::OpenRead($package)
try {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = ([System.BitConverter]::ToString($sha256.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
} finally {
    $stream.Dispose()
}
[System.IO.File]::WriteAllText($hashFile, "$hash  $([System.IO.Path]::GetFileName($package))`n", $utf8NoBom)
Write-Host "Created unsigned Microsoft Store package: $package"
Write-Host "SHA-256: $hash"
