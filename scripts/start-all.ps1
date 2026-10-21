$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$logsDir = Join-Path $root 'logs'
$pidFile = Join-Path $logsDir 'service-pids.json'
$mvnCmd = Join-Path $root 'mvnw.cmd'

function Get-JavaMajorVersion {
    param([string]$JavaExe)

    $javaHome = Split-Path -Parent (Split-Path -Parent $JavaExe)
    $releaseFile = Join-Path $javaHome 'release'
    if (Test-Path -LiteralPath $releaseFile) {
        $releaseLine = Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="([0-9]+)' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($releaseLine -and $releaseLine.Matches.Count -gt 0) {
            return [int]$releaseLine.Matches[0].Groups[1].Value
        }
    }

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $line = (& $JavaExe -version 2>&1 | ForEach-Object { $_.ToString() } | Select-Object -First 1)
        if ($line -match 'version "([0-9]+)') {
            return [int]$Matches[1]
        }
    } catch {
        return 0
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return 0
}

function Add-JavaCandidate {
    param(
        [System.Collections.Generic.List[string]]$Candidates,
        [string]$JavaHome
    )
    if ([string]::IsNullOrWhiteSpace($JavaHome)) { return }
    $javaExe = Join-Path $JavaHome 'bin\java.exe'
    if ((Test-Path -LiteralPath $javaExe) -and -not $Candidates.Contains($JavaHome)) {
        $Candidates.Add($JavaHome)
    }
}

function Test-TcpPort {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(800)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-ForHealth {
    param([string]$Name, [int]$Port, [int]$Attempts = 60)
    $url = "http://127.0.0.1:$Port/actuator/health"
    for ($i = 1; $i -le $Attempts; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                Write-Host "[$Name] 启动成功：$url"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "$Name 未在预期时间内通过健康检查：$url"
}

Write-Host '============================================'
Write-Host ' RiskLendPro 本地启动'
Write-Host '============================================'

$javaCandidates = New-Object 'System.Collections.Generic.List[string]'
Add-JavaCandidate $javaCandidates $env:JAVA_HOME

$jbrCandidates = @()
# 注意：对不存在的盘符使用通配路径时，Get-ChildItem 会在参数绑定阶段就抛
# ParameterBindingException（-Directory 找不到），-ErrorAction SilentlyContinue 挡不住，
# 所以先确认路径根存在再扫描，避免没有 F: 盘的机器直接启动失败。
foreach ($jbrPattern in @('F:\IntelliJ IDEA*\jbr', 'C:\Program Files\JetBrains\*\jbr')) {
    $patternRoot = [System.IO.Path]::GetPathRoot($jbrPattern)
    if (-not $patternRoot -or -not (Test-Path -LiteralPath $patternRoot)) { continue }
    $jbrCandidates += Get-ChildItem -Path $jbrPattern -Directory -ErrorAction SilentlyContinue
}
foreach ($item in $jbrCandidates) {
    Add-JavaCandidate $javaCandidates $item.FullName
}

$jdkRoots = @(
    'C:\Program Files\Java',
    'C:\Program Files\Eclipse Adoptium',
    'C:\Program Files\Microsoft',
    'C:\Program Files\Amazon Corretto',
    'C:\Program Files\Zulu',
    'C:\Program Files\BellSoft',
    'F:\java'
)
foreach ($jdkRoot in $jdkRoots) {
    if (-not (Test-Path -LiteralPath $jdkRoot)) { continue }
    foreach ($dir in (Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue)) {
        if ($dir.Name -match '21') {
            Add-JavaCandidate $javaCandidates $dir.FullName
        }
    }
}

$userJdks = Join-Path $HOME '.jdks'
if (Test-Path -LiteralPath $userJdks) {
    foreach ($dir in (Get-ChildItem -LiteralPath $userJdks -Directory -ErrorAction SilentlyContinue)) {
        Add-JavaCandidate $javaCandidates $dir.FullName
    }
}

$selectedJavaHome = $null
foreach ($candidate in $javaCandidates) {
    $major = Get-JavaMajorVersion (Join-Path $candidate 'bin\java.exe')
    if ($major -ge 21) {
        $selectedJavaHome = $candidate
        break
    }
}

if (-not $selectedJavaHome) {
    throw '未找到 JDK 21 或更高版本。请安装 JDK 21，或设置 JAVA_HOME 指向 JDK 21 后重试。'
}

$env:JAVA_HOME = $selectedJavaHome
Write-Host "使用 JDK：$selectedJavaHome"
& (Join-Path $selectedJavaHome 'bin\java.exe') -version

if (-not (Test-Path -LiteralPath $mvnCmd)) {
    throw "找不到 Maven Wrapper：$mvnCmd"
}

Write-Host ''
Write-Host '正在编译项目（首次运行可能需要下载依赖）...'
& $mvnCmd '-DskipTests' 'package'
if ($LASTEXITCODE -ne 0) {
    throw "项目编译失败，退出码：$LASTEXITCODE"
}

# 根 pom 的 <modules> 只聚合了 common-api / common-runtime / risk-service，
# gateway、user-service、loan-service、cs-service 需要单独打包，否则下面找不到 jar。
foreach ($module in @('gateway', 'user-service', 'loan-service', 'cs-service')) {
    Write-Host "正在编译 services/$module ..."
    & $mvnCmd '-DskipTests' '-f' "services/$module/pom.xml" 'package'
    if ($LASTEXITCODE -ne 0) {
        throw "services/$module 编译失败，退出码：$LASTEXITCODE"
    }
}

New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

$services = @(
    [PSCustomObject]@{ Name = 'user-service'; Port = 8081; Jar = 'services\user-service\target\user-service-0.0.1-SNAPSHOT.jar' },
    [PSCustomObject]@{ Name = 'risk-service'; Port = 8082; Jar = 'services\risk-service\target\risk-service-0.0.1-SNAPSHOT.jar' },
    [PSCustomObject]@{ Name = 'loan-service'; Port = 8083; Jar = 'services\loan-service\target\loan-service-0.0.1-SNAPSHOT.jar' },
    [PSCustomObject]@{ Name = 'cs-service'; Port = 8084; Jar = 'services\cs-service\target\cs-service-0.0.1-SNAPSHOT.jar' },
    [PSCustomObject]@{ Name = 'gateway'; Port = 8088; Jar = 'services\gateway\target\gateway-0.0.1-SNAPSHOT.jar' }
)

$stopScript = Join-Path $PSScriptRoot 'stop-all.ps1'
if (Test-Path -LiteralPath $pidFile) {
    & $stopScript -Quiet
}

foreach ($service in $services) {
    if (Test-TcpPort $service.Port) {
        throw "端口 $($service.Port) 已被占用，无法启动 $($service.Name)。请先关闭占用程序。"
    }
}

$started = New-Object System.Collections.ArrayList
try {
    foreach ($service in $services) {
        $jarPath = Join-Path $root $service.Jar
        if (-not (Test-Path -LiteralPath $jarPath)) {
            throw "找不到服务包：$jarPath"
        }

        $outLog = Join-Path $logsDir "$($service.Name).out.log"
        $errLog = Join-Path $logsDir "$($service.Name).err.log"
        Set-Content -LiteralPath $outLog -Value '' -Encoding UTF8
        Set-Content -LiteralPath $errLog -Value '' -Encoding UTF8

        $process = Start-Process -FilePath (Join-Path $selectedJavaHome 'bin\java.exe') -ArgumentList @('-jar', $jarPath) -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru

        Start-Sleep -Seconds 2
        if ($process.HasExited) {
            throw "$($service.Name) 启动后立即退出，退出码 $($process.ExitCode)。请查看 $outLog 和 $errLog。"
        }

        [void]$started.Add([PSCustomObject]@{
            name = $service.Name
            pid = $process.Id
            port = $service.Port
            jar = $jarPath
            stdout = $outLog
            stderr = $errLog
        })

        Write-Host "[$($service.Name)] 已启动 (PID $($process.Id)，端口 $($service.Port))"
        Wait-ForHealth -Name $service.Name -Port $service.Port
    }

    $started | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $pidFile -Encoding UTF8
    Write-Host ''
    Write-Host '全部服务启动完成。'
    Write-Host '业务入口：http://localhost:8088/api/v1'
    Write-Host '日志目录：logs'
    Write-Host '停止服务：双击 stop.bat'
} catch {
    foreach ($item in $started) {
        Stop-Process -Id $item.pid -Force -ErrorAction SilentlyContinue
    }
    Write-Error $_
    exit 1
}
