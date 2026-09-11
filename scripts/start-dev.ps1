param([switch]$SkipFrontend, [switch]$SkipBackend, [switch]$StartRocketMq)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runDir = Join-Path $repoRoot '.codex-run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$stdinPath = Join-Path $runDir 'dev-start.stdin'
if (-not (Test-Path -LiteralPath $stdinPath)) { New-Item -ItemType File -Path $stdinPath | Out-Null }
. (Join-Path $PSScriptRoot 'dev-runtime.ps1')
. (Join-Path $PSScriptRoot 'local-runtime.ps1')

function Invoke-LocalDocker {
    param([string[]]$DockerArguments, [string]$Label, [int]$TimeoutSeconds = 30, [switch]$Quiet)
    Write-Host "[STEP] $Label (timeout $($TimeoutSeconds)s)"
    $result = Invoke-DevCommand -FilePath $dockerPath -ArgumentList $DockerArguments -WorkingDirectory $repoRoot -TimeoutSeconds $TimeoutSeconds -Label $Label -LogPath (Join-Path $runDir 'docker-last.log') -Quiet:$Quiet
    if ($result.ExitCode -ne 0) {
        throw "$Label failed (exit $($result.ExitCode)). $($result.Error.Trim()) Log: $runDir\docker-last.log"
    }
    return $result.Output
}

try {
    Write-Host '[STEP] safeguard startup entered; checking Docker Desktop'
    $dockerPath = (Get-Command docker.exe -ErrorAction Stop).Source
    $null = Invoke-LocalDocker @('info','--format','{{.ServerVersion}}') 'Docker Desktop check' -Quiet
    Write-Host '[OK] Docker Desktop is ready'
    Assert-LocalRuntimePortOwnership
    & (Join-Path $PSScriptRoot 'start-tei.ps1')
    $running = Invoke-LocalDocker @('ps','--format','{{.Names}}') 'Inspect running containers' -Quiet
    $containerNames = $running -split '\r?\n'
    if ('safeguard-postgres' -notin $containerNames -or 'safeguard-rustfs' -notin $containerNames) {
        & (Join-Path $PSScriptRoot 'windows\start-required-middleware.ps1')
    }
    foreach ($dependency in @(@(15432,'PostgreSQL'), @(9000,'RustFS API'))) {
        $deadline = [DateTime]::UtcNow.AddSeconds(60)
        while (-not (Test-DevPort $dependency[0])) {
            if ([DateTime]::UtcNow -ge $deadline) { throw "$($dependency[1]) unavailable on $($dependency[0])." }
            Write-Host "[WAIT] $($dependency[1]) port $($dependency[0])"
            Start-Sleep -Seconds 1
        }
        Write-Host "[OK] $($dependency[1]) reachable on $($dependency[0])"
    }
    if ('safeguard-redis-phase1' -in $containerNames) {
        $null = Invoke-LocalDocker @('stop','safeguard-redis-phase1') 'Stop legacy Redis container; preserve its volume'
    }
    if ('safeguard-redis-dev' -notin $containerNames) {
        $null = Invoke-LocalDocker @('compose','--ansi','never','-f','resources/docker/redis-local.compose.yaml','up','-d') 'Start Redis' 180
    }
    else { Write-Host '[OK] Redis container safeguard-redis-dev is already running' }
    if (-not (Test-DevPort 6379)) { throw 'Redis unavailable on 6379.' }
    Write-Host '[OK] Redis reachable on 6379'
    if ($StartRocketMq) {
        $null = Invoke-LocalDocker @('compose','--ansi','never','-f','resources/docker/rocketmq-stack-5.2.0.compose.yaml','up','-d') 'Start RocketMQ' 180
    }
    if (-not (Test-DevPort 9876)) { throw 'RocketMQ unavailable on 9876. Use -StartRocketMq.' }
    Write-Host '[OK] RocketMQ NameServer reachable on 9876'
    if (Test-DevPort 11434) { Write-Host '[OK] Ollama available on 11434' }
    else { Write-Host '[WARN] Ollama unavailable (optional)' }

    if (-not $SkipBackend) {
        $backendProcess = $null
        $backendLog = Join-Path $runDir 'safeguard-backend.out.log'
        if (-not (Test-DevPort 9090)) {
            $javaPath = (Get-Command java.exe -ErrorAction Stop).Source
            Write-Host "[STEP] Build safeguard backend; log: $runDir\safeguard-build.log"
            $buildCode = '& ''.\mvnw.cmd'' -pl bootstrap -am package ''-DskipTests'' ''-Dspotless.apply.skip=true'' ''-Dspotless.check.skip=true''; exit $LASTEXITCODE'
            $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($buildCode))
            $build = Invoke-DevCommand -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -ArgumentList @('-NoProfile','-NonInteractive','-EncodedCommand',$encoded) -WorkingDirectory $repoRoot -TimeoutSeconds 600 -Label 'Maven package' -LogPath (Join-Path $runDir 'safeguard-build.log') -Quiet
            if ($build.ExitCode -ne 0) { throw "Maven failed (exit $($build.ExitCode)). Log: $runDir\safeguard-build.log" }
            $jarPath = Join-Path $repoRoot 'bootstrap\target\bootstrap-0.0.1-SNAPSHOT.jar'
            if (-not (Test-Path -LiteralPath $jarPath)) { throw "Missing backend JAR: $jarPath" }
            Write-Host '[STEP] Start safeguard backend (local profile, 9090)'
            $backendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar',(ConvertTo-DevArgument $jarPath),'--spring.profiles.active=local','--server.port=9090') -WorkingDirectory $repoRoot -RedirectStandardInput $stdinPath -RedirectStandardOutput $backendLog -RedirectStandardError (Join-Path $runDir 'safeguard-backend.err.log') -WindowStyle Hidden -PassThru
        }
        Wait-DevHttp -Uri 'http://127.0.0.1:9090/api/safeguard-agent/' -Kind Backend -Label 'safeguard backend' -Process $backendProcess -LogPath $backendLog
    }
    if (-not $SkipFrontend) {
        $frontendProcess = $null
        $frontendLog = Join-Path $runDir 'safeguard-frontend.out.log'
        if (-not (Test-DevPort 5173)) {
            $nodePath = (Get-Command node.exe -ErrorAction Stop).Source
            $vitePath = Join-Path $repoRoot 'frontend\node_modules\vite\bin\vite.js'
            if (-not (Test-Path -LiteralPath $vitePath)) { throw 'Frontend dependencies missing. Run npm ci in frontend/.' }
            Write-Host '[STEP] Start safeguard frontend (5173, strict port)'
            $frontendProcess = Start-Process -FilePath $nodePath -ArgumentList @((ConvertTo-DevArgument $vitePath),'--host','127.0.0.1','--port','5173','--strictPort') -WorkingDirectory (Join-Path $repoRoot 'frontend') -RedirectStandardInput $stdinPath -RedirectStandardOutput $frontendLog -RedirectStandardError (Join-Path $runDir 'safeguard-frontend.err.log') -WindowStyle Hidden -PassThru
        }
        Wait-DevHttp -Uri 'http://127.0.0.1:5173/' -Kind Frontend -Label 'safeguard frontend' -Process $frontendProcess -LogPath $frontendLog
    }
    Write-Host ''
    if (-not $SkipFrontend) { Write-Host 'Frontend URL: http://127.0.0.1:5173' }
    if (-not $SkipBackend) { Write-Host 'Backend URL:  http://127.0.0.1:9090/api/safeguard-agent' }
}
catch {
    Write-Host "[FAILED] safeguard startup: $($_.Exception.Message)"
    throw
}
