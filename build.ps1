param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $JavaHome) { $JavaHome = 'D:\tools\jdk21' }
$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $PSScriptRoot '.tools\android-user'
& ./gradlew build
if ($LASTEXITCODE -ne 0) { throw 'Gradle build 失败' }
