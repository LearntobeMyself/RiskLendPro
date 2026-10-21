param(
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root 'logs\service-pids.json'

if (-not (Test-Path -LiteralPath $pidFile)) {
    if (-not $Quiet) { Write-Host '没有发现由 start.bat 启动的服务记录。' }
    exit 0
}

try {
    $entries = @((Get-Content -LiteralPath $pidFile -Raw -Encoding UTF8 | ConvertFrom-Json))
} catch {
    if (-not $Quiet) { Write-Warning "服务记录无法解析：$pidFile" }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    exit 0
}

foreach ($entry in $entries) {
    $processId = [int]$entry.pid
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        if (-not $Quiet) { Write-Host "$($entry.name) 已停止。" }
        continue
    }

    try {
        Stop-Process -Id $processId -Force
        if (-not $Quiet) { Write-Host "已停止 $($entry.name) (PID $processId)。" }
    } catch {
        if (-not $Quiet) { Write-Warning "停止 $($entry.name) 失败：$($_.Exception.Message)" }
    }
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
