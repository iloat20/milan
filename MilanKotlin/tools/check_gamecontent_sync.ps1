#!/usr/bin/env pwsh
<#
.SYNOPSIS
    CI 同步校验：data.json 变更后 GameContent.kt 必须同步更新。

.DESCRIPTION
    重新运行 generate_gamecontent.py 并 diff 生成结果与仓库中的 GameContent.kt。
    不一致则报错（CI 红灯），防止 data.json 改了但兜底代码未同步。

.EXAMPLE
    .\tools\check_gamecontent_sync.ps1
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$generator = Join-Path $scriptDir "generate_gamecontent.py"
$gameContentPath = Join-Path $projectRoot "app\src\main\java\com\milan\game\services\GameContent.kt"

# 找 Python
$python = $null
foreach ($cmd in @("python", "python3", "py")) {
    if (Get-Command $cmd -ErrorAction SilentlyContinue) {
        $python = $cmd
        break
    }
}
if (-not $python) {
    Write-Error "找不到 Python，请安装后重试。"
    exit 1
}

# 生成临时副本
$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "gc_sync_check_$(Get-Random)"
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$tempFile = Join-Path $tempDir "GameContent.kt"

try {
    # 复制当前仓库中的文件到临时位置
    Copy-Item $gameContentPath $tempFile

    # 重新生成（覆盖仓库中的文件）
    Write-Host "重新生成 GameContent.kt ..." -ForegroundColor Cyan
    Push-Location $projectRoot
    & $python $generator
    $exitCode = $LASTEXITCODE
    Pop-Location

    if ($exitCode -ne 0) {
        Write-Error "生成器运行失败 (exit code $exitCode)"
        exit 1
    }

    # 对比
    $existing = Get-Content $tempFile -Raw
    $generated = Get-Content $gameContentPath -Raw

    if ($existing -eq $generated) {
        Write-Host "OK: GameContent.kt 与 data.json 同步。" -ForegroundColor Green
        exit 0
    } else {
        Write-Host "FAIL: GameContent.kt 与 data.json 不同步！" -ForegroundColor Red
        Write-Host ""
        Write-Host "请在修改 data.json 后运行以下命令重新生成：" -ForegroundColor Yellow
        Write-Host "  python tools/generate_gamecontent.py" -ForegroundColor Yellow
        Write-Host ""
        # 恢复原始文件
        Copy-Item $tempFile $gameContentPath -Force
        exit 1
    }
} finally {
    Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}
