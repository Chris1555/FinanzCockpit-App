param(
    [string]$SigningDirectory,
    [string]$OutputApk = [System.IO.Path]::GetFullPath(
        (Join-Path $PSScriptRoot '..\..\..\Android\FinanzCockpit-2.0.1-install.apk')
    )
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($SigningDirectory)) {
    throw 'Für einen Release-Build muss -SigningDirectory ausdrücklich angegeben werden. Es wird kein Standardpfad verwendet.'
}

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME fehlt. Für den Build wird JDK 17 benötigt.' }
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

$credentialsPath = Join-Path $SigningDirectory 'SIGNATUR-ZUGANGSDATEN.json'
if (-not (Test-Path -LiteralPath $credentialsPath)) {
    throw "Signatur-Zugangsdaten fehlen: $credentialsPath"
}
$credentials = Get-Content -LiteralPath $credentialsPath -Raw | ConvertFrom-Json
$keyStorePath = Join-Path $SigningDirectory $credentials.keyStoreFile
if (-not (Test-Path -LiteralPath $keyStorePath)) {
    throw "Privater Signaturschlüssel fehlt: $keyStorePath"
}

$env:FINANZCOCKPIT_UPDATE_KEYSTORE = $keyStorePath
$env:FINANZCOCKPIT_UPDATE_STORE_PASSWORD = [string]$credentials.storePassword
$env:FINANZCOCKPIT_UPDATE_KEY_ALIAS = [string]$credentials.keyAlias
$env:FINANZCOCKPIT_UPDATE_KEY_PASSWORD = [string]$credentials.keyPassword

try {
    Push-Location $PSScriptRoot
    try {
        & .\gradlew.bat testDebugUnitTest lintDebug assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Tests oder Release-Build sind fehlgeschlagen.' }
    } finally {
        Pop-Location
    }

    $sourceApk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $sourceApk)) {
        throw "Signierte Release-APK wurde nicht gefunden: $sourceApk"
    }

    $buildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if (-not $buildTools) { throw 'Android Build-Tools wurden nicht gefunden.' }
    $apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
    $zipalign = Join-Path $buildTools.FullName 'zipalign.exe'

    $signatureOutput = & $apksigner verify --verbose --print-certs $sourceApk
    if ($LASTEXITCODE -ne 0) { throw 'Die APK-Signatur ist ungültig.' }
    $digestLine = $signatureOutput | Where-Object { $_ -match 'Signer #1 certificate SHA-256 digest:' } |
        Select-Object -First 1
    $actualDigest = ($digestLine -replace '^.*digest:\s*', '').Replace(':', '').Trim().ToLower()
    $expectedDigest = ([string]$credentials.certificateSha256).Replace(':', '').Trim().ToLower()
    if (-not $actualDigest -or $actualDigest -ne $expectedDigest) {
        throw "Falsches Signaturzertifikat. Erwartet: $expectedDigest, erhalten: $actualDigest"
    }

    & $zipalign -c -P 16 -v 4 $sourceApk | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Die 16-KB-Zip-Ausrichtung der APK ist ungültig.' }

    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $OutputApk)) | Out-Null
    Copy-Item -LiteralPath $sourceApk -Destination $OutputApk -Force
    Write-Host "Installierbare APK erstellt: $OutputApk"
    Write-Host "APK SHA-256: $((Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLower())"
    Write-Host "Zertifikat SHA-256: $actualDigest"
} finally {
    Remove-Item Env:FINANZCOCKPIT_UPDATE_KEYSTORE -ErrorAction SilentlyContinue
    Remove-Item Env:FINANZCOCKPIT_UPDATE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:FINANZCOCKPIT_UPDATE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:FINANZCOCKPIT_UPDATE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $credentials = $null
}
