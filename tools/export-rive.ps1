param([string]$RiveCli = $env:RIVE_CLI)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $RiveCli) { $RiveCli = Join-Path $env:USERPROFILE '.rive\bin\rive.exe' }
if (-not (Test-Path -LiteralPath $RiveCli)) { throw '找不到 Rive CLI，请设置 RIVE_CLI' }

& $RiveCli rive --verify --format=json
if ($LASTEXITCODE -ne 0) { throw 'RML 验证失败' }
& $RiveCli inspect rive --summary
if ($LASTEXITCODE -ne 0) { throw 'RML 结构检查失败' }

# 仅导出到本地；Rive 要求先通过 rive login 登录。
& $RiveCli rive --publish --rev=rive/build/bongocat.rev --format=json
if ($LASTEXITCODE -ne 0) { throw 'Rive 正式导出失败；请查看上方登录、网络或编译错误后重试' }
$model = Join-Path (Get-Location) 'rive/build/bongocat.riv'
if (-not (Test-Path -LiteralPath $model)) { throw 'Rive 没有生成 bongocat.riv' }
New-Item -ItemType Directory -Force app/src/main/res/raw | Out-Null
Copy-Item -LiteralPath $model -Destination app/src/main/res/raw/bongocat.riv
$sourceHash = (Get-FileHash -LiteralPath rive/scene.rml -Algorithm SHA256).Hash.ToLowerInvariant()
$configHash = (Get-FileHash -LiteralPath rive/rive.yaml -Algorithm SHA256).Hash.ToLowerInvariant()
$runtimeHash = (Get-FileHash -LiteralPath app/src/main/res/raw/bongocat.riv -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path (Get-Location) 'rive/export.sha256'),
    "$sourceHash  scene.rml`n$configHash  rive.yaml`n$runtimeHash  ../app/src/main/res/raw/bongocat.riv`n", [Text.Encoding]::ASCII)
