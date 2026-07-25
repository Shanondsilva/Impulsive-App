# Safe source export. Refuses to produce an archive containing secrets.
param([string]$OutputName = "impulsive-src-safe.zip")
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$excludeDirs = @('.git','.idea','.vscode','.agents','.kotlin','.gradle',
                 'build','node_modules','release-keystore')
$excludeNamePatterns = @('*.jks','*.keystore','*.p12','*.pfx','*.pem','*.key',
                         '*.zip','*.apk','*.aab','*.log','local.properties',
                         'secrets.properties*','changed-*.txt')

function Test-IsSecretEntry([string]$relPath) {
    $name = [System.IO.Path]::GetFileName($relPath)
    if ($name -match '\.(jks|keystore|p12|pfx|pem|key)$') { return $true }
    if ($name -ieq 'keystore.properties') { return $true }
    if (($name -ilike 'keystore.properties.*') -and ($name -inotlike '*PLACEHOLDER*')) { return $true }
    if (($name -imatch '^google-services.*\.json$') -and ($name -inotmatch '(example|placeholder)')) { return $true }
    if (($name -imatch 'service[-_]?account.*\.json$') -or ($name -imatch 'firebase-adminsdk')) { return $true }
    if (($name -ieq '.env') -or ($name -ilike '.env.*')) { return $true }
    return $false
}

$outputPath = Join-Path $repoRoot $OutputName
if (Test-Path $outputPath) { Remove-Item $outputPath -Force }

$files = @(Get-ChildItem -Path $repoRoot -Recurse -File -Force | Where-Object {
    $rel = $_.FullName.Substring($repoRoot.Length + 1)
    $parts = $rel -split '[\\/]'
    foreach ($p in $parts) { if ($excludeDirs -contains $p) { return $false } }
    if (Test-IsSecretEntry $rel) { return $false }
    $leaf = $parts[-1]
    foreach ($pat in $excludeNamePatterns) { if ($leaf -like $pat) { return $false } }
    return $true
})

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($outputPath, 'Create')
try {
    foreach ($f in $files) {
        $rel = ($f.FullName.Substring($repoRoot.Length + 1)) -replace '\\','/'
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $f.FullName, $rel,
            [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $zip.Dispose() }

$violations = @()
$zipRead = [System.IO.Compression.ZipFile]::OpenRead($outputPath)
try {
    foreach ($e in $zipRead.Entries) {
        if (Test-IsSecretEntry $e.FullName) { $violations += $e.FullName }
    }
} finally { $zipRead.Dispose() }

if ($violations.Count -gt 0) {
    Remove-Item $outputPath -Force
    Write-Error ("SECRET SCAN FAILED - archive deleted. Offending entries:`n" +
        ($violations -join "`n"))
    exit 1
}
Write-Host ("Entries: {0}   Size: {1:N0} bytes" -f $files.Count, (Get-Item $outputPath).Length)
Write-Host ("SECRET SCAN: CLEAN -> " + $outputPath)
