param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$androidNamespace =
    "http://schemas.android.com/apk/res/android"

$intermediatesPath =
    Join-Path `
        $ProjectRoot `
        "app\build\intermediates"

if (
    -not (
        Test-Path `
            -LiteralPath `
            $intermediatesPath
    )
) {
    throw (
        "Android build intermediates were not found. " +
        "Run :app:processReleaseMainManifest first."
    )
}

$candidates =
    Get-ChildItem `
        -LiteralPath `
        $intermediatesPath `
        -Recurse `
        -Filter `
        "AndroidManifest.xml" `
        -File |
    Where-Object {
        $_.FullName -match `
            "[\\/]merged_manifests?[\\/]" `
        -and
        $_.FullName -match `
            "[\\/]release[\\/]"
    } |
    Sort-Object `
        LastWriteTime `
        -Descending

$mergedManifest =
    $candidates |
    Select-Object `
        -First `
        1

if ($null -eq $mergedManifest) {
    throw (
        "No merged release AndroidManifest.xml was found. " +
        "Run :app:processReleaseMainManifest and retry."
    )
}

Write-Host (
    "Checking merged release manifest: " +
    $mergedManifest.FullName
)

[xml]$xml =
    Get-Content `
        -LiteralPath `
        $mergedManifest.FullName `
        -Raw

$namespaceManager =
    New-Object `
        System.Xml.XmlNamespaceManager(
            $xml.NameTable
        )

$namespaceManager.AddNamespace(
    "android",
    $androidNamespace
)

function Get-AndroidAttribute {
    param(
        [System.Xml.XmlNode]$Node,
        [string]$Name
    )

    if ($null -eq $Node) {
        return $null
    }

    return $Node.GetAttribute(
        $Name,
        $androidNamespace
    )
}

function Require-Node {
    param(
        [string]$XPath,
        [string]$Description
    )

    $node =
        $xml.SelectSingleNode(
            $XPath,
            $namespaceManager
        )

    if ($null -eq $node) {
        throw (
            "Missing required merged-manifest entry: " +
            $Description
        )
    }

    return $node
}

# AD_ID must not survive manifest merging.
$adIdNode =
    $xml.SelectSingleNode(
        "//*[@android:name=" +
        "'com.google.android.gms.permission.AD_ID']",
        $namespaceManager
    )

if ($null -ne $adIdNode) {
    throw (
        "Privacy verification failed: " +
        "com.google.android.gms.permission.AD_ID " +
        "is present in the merged release manifest."
    )
}

$autoLogNode =
    Require-Node `
        -XPath (
            "/manifest/application/meta-data" +
            "[@android:name=" +
            "'com.facebook.sdk.AutoLogAppEventsEnabled']"
        ) `
        -Description `
        "Facebook AutoLogAppEventsEnabled"

$autoLogValue =
    Get-AndroidAttribute `
        -Node `
        $autoLogNode `
        -Name `
        "value"

if ($autoLogValue -ne "false") {
    throw (
        "Privacy verification failed: " +
        "AutoLogAppEventsEnabled must be false."
    )
}

$advertiserIdNode =
    Require-Node `
        -XPath (
            "/manifest/application/meta-data" +
            "[@android:name=" +
            "'com.facebook.sdk.AdvertiserIDCollectionEnabled']"
        ) `
        -Description `
        "Facebook AdvertiserIDCollectionEnabled"

$advertiserIdValue =
    Get-AndroidAttribute `
        -Node `
        $advertiserIdNode `
        -Name `
        "value"

if ($advertiserIdValue -ne "false") {
    throw (
        "Privacy verification failed: " +
        "AdvertiserIDCollectionEnabled must be false."
    )
}

# Facebook Login configuration must remain present.
Require-Node `
    -XPath (
        "/manifest/application/meta-data" +
        "[@android:name=" +
        "'com.facebook.sdk.ApplicationId']"
    ) `
    -Description `
    "Facebook ApplicationId" |
    Out-Null

Require-Node `
    -XPath (
        "/manifest/application/meta-data" +
        "[@android:name=" +
        "'com.facebook.sdk.ClientToken']"
    ) `
    -Description `
    "Facebook ClientToken" |
    Out-Null

Require-Node `
    -XPath (
        "/manifest/application/activity" +
        "[@android:name=" +
        "'com.facebook.FacebookActivity']"
    ) `
    -Description `
    "FacebookActivity" |
    Out-Null

Require-Node `
    -XPath (
        "/manifest/application/activity" +
        "[@android:name=" +
        "'com.facebook.CustomTabActivity']"
    ) `
    -Description `
    "Facebook CustomTabActivity" |
    Out-Null

Write-Host (
    "Facebook privacy manifest verification passed."
)

Write-Host (
    "Confirmed: " +
    "AutoLogAppEventsEnabled=false"
)

Write-Host (
    "Confirmed: " +
    "AdvertiserIDCollectionEnabled=false"
)

Write-Host (
    "Confirmed: " +
    "GMS AD_ID permission absent"
)

Write-Host (
    "Confirmed: " +
    "Facebook Login manifest components preserved"
)
