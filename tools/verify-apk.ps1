param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Apk,
    [string]$BuildToolsDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$expectedSigner = 'f89eb1342441f6dd6a62de79f1a54cc527ce3838c9f26c45f28084d8dc501eb4'
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
if (-not $BuildToolsDirectory) {
    $BuildToolsDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) '.tools/android-sdk/build-tools/36.0.0'
}
$signer = Join-Path $BuildToolsDirectory 'apksigner.bat'
$aapt = Join-Path $BuildToolsDirectory 'aapt.exe'
if (-not (Test-Path -LiteralPath $signer) -or -not (Test-Path -LiteralPath $aapt)) {
    throw '请用 -BuildToolsDirectory 指定 Android SDK Build Tools 目录。'
}

# 先验证签名覆盖的 APK 内容，再比对预先固定的发布证书。
try {
    $verification = (& $signer verify --verbose --print-certs $apkPath 2>&1 | Out-String)
} catch {
    throw 'APK 完整性或签名校验失败。'
}
if ($LASTEXITCODE -ne 0) { throw 'APK 完整性或签名校验失败。' }
if ($verification -notmatch 'Verified using v2 scheme \(APK Signature Scheme v2\): true') {
    throw '缺少有效的 APK v2 完整性签名。'
}
$fingerprints = [regex]::Matches($verification, '(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})\s*$')
if ($fingerprints.Count -ne 1 -or $fingerprints[0].Groups[1].Value -cne $expectedSigner) {
    throw '签名身份不匹配：该文件不是本项目已信任的发布签名。'
}
$badging = (& $aapt dump badging $apkPath 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='com.yuyu.bongocat' versionCode='([0-9]+)' versionName='([^']+)'") {
    throw 'APK 包名或版本信息不正确。'
}
$versionCode = $Matches[1]
$versionName = $Matches[2]
if ($badging -match '(?m)^application-debuggable') { throw '这是调试包，不能作为正式发布包。' }
$sha256 = [System.Security.Cryptography.SHA256]::Create()
$inputStream = [System.IO.File]::OpenRead($apkPath)
try {
    $fileHash = [System.BitConverter]::ToString($sha256.ComputeHash($inputStream)).Replace('-', '').ToLowerInvariant()
} finally {
    $inputStream.Dispose()
    $sha256.Dispose()
}
[pscustomobject]@{
    Result = '来源和完整性校验通过'
    Version = $versionName
    VersionCode = $versionCode
    V3 = $verification.Contains('Verified using v3 scheme (APK Signature Scheme v3): true')
    SignerSHA256 = $expectedSigner
    FileSHA256 = $fileHash
}
