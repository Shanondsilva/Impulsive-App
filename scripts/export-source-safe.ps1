# Safe source export. Refuses to produce an archive containing secrets,
# privacy-sensitive diagnostics, build artifacts or excluded directories.
#
# One policy (Test-IsForbiddenExportEntry), two enforcement points:
# files are filtered before compression AND every entry of the finished
# archive is re-validated. Collection and validation therefore cannot drift
# apart the way they previously did.
param([string]$OutputName = "impulsive-src-safe.zip")
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$excludeDirs = @('.git','.idea','.vscode','.agents','.kotlin','.gradle',
                 'build','node_modules','release-keystore')
# '*.log' covers conventional log files; the three *.txt patterns cover the
# project's diagnostic dumps, which would otherwise slip through as plain text.
$excludeNamePatterns = @('*.jks','*.keystore','*.p12','*.pfx','*.pem','*.key',
                         '*.zip','*.apk','*.aab','*.log','local.properties',
                         'secrets.properties*','changed-*.txt',
                         '*-log.txt','*_log.txt','*logcat*.txt')

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

# Accepts a relative path in either filesystem or ZIP-entry slash style.
function Test-IsExcludedDirectoryEntry([string]$relPath) {
    $parts = $relPath -split '[\\/]'
    foreach ($part in $parts) {
        if ($excludeDirs -contains $part) { return $true }
    }
    return $false
}

function Test-IsExcludedNameEntry([string]$relPath) {
    $name = [System.IO.Path]::GetFileName($relPath)
    foreach ($pattern in $excludeNamePatterns) {
        if ($name -ilike $pattern) { return $true }
    }
    return $false
}

# The single source of truth for what may never enter a safe export.
function Test-IsForbiddenExportEntry([string]$relPath) {
    if (Test-IsExcludedDirectoryEntry $relPath) { return $true }
    if (Test-IsSecretEntry $relPath) { return $true }
    if (Test-IsExcludedNameEntry $relPath) { return $true }
    return $false
}

$outputPath = Join-Path $repoRoot $OutputName
if (Test-Path $outputPath) { Remove-Item $outputPath -Force }

$files = @(Get-ChildItem -Path $repoRoot -Recurse -File -Force | Where-Object {
    $rel = $_.FullName.Substring($repoRoot.Length + 1)
    return -not (Test-IsForbiddenExportEntry $rel)
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

# The finished archive is the final authority: even if collection regresses,
# a forbidden entry here deletes the archive and fails the run.
$violations = @()
$zipRead = [System.IO.Compression.ZipFile]::OpenRead($outputPath)
try {
    foreach ($e in $zipRead.Entries) {
        if (Test-IsForbiddenExportEntry $e.FullName) { $violations += $e.FullName }
    }
} finally { $zipRead.Dispose() }

if ($violations.Count -gt 0) {
    Remove-Item $outputPath -Force
    Write-Error ("SAFE EXPORT POLICY SCAN FAILED - archive deleted. Offending entries:`n" +
        ($violations -join "`n"))
    exit 1
}
Write-Host ("Entries: {0}   Size: {1:N0} bytes" -f $files.Count, (Get-Item $outputPath).Length)
Write-Host ("SAFE EXPORT POLICY SCAN: CLEAN -> " + $outputPath)
