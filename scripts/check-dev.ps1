$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-runtime.ps1')
. (Join-Path $PSScriptRoot 'local-runtime.ps1')
$failed = $false
try {
    Write-Host '[STEP] Checking Docker Desktop (timeout 15s)'
    $docker = Invoke-DevCommand -FilePath (Get-Command docker.exe -ErrorAction Stop).Source -ArgumentList @('info','--format','{{.ServerVersion}}') -TimeoutSeconds 15 -Label 'Docker Desktop check' -Quiet
    if ($docker.ExitCode -ne 0) { throw 'Docker info returned an error.' }
    Write-Host '[OK] Docker Desktop ready'
}
catch { Write-Host "[FAILED] $($_.Exception.Message)"; $failed = $true }
try { Assert-LocalRuntimePortOwnership; Write-Host '[OK] Local runtime port ownership' } catch { Write-Host "[FAIL] $($_.Exception.Message)"; $failed = $true }
if (Get-DockerPortOwners $script:TeiHostPort -contains $script:TeiContainerName) { Write-Host '[OK] TEI container' } else { Write-Host '[FAIL] TEI container'; $failed = $true }
try { $health = (Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($script:TeiHostPort)/health" -TimeoutSec 5).StatusCode -eq 200 } catch { $health = $false }
if ($health) { Write-Host '[OK] TEI endpoint 18083' } else { Write-Host '[FAIL] TEI endpoint 18083'; $failed = $true }
if (Test-TeiEmbeddingSmoke) { Write-Host '[OK] bge-m3 embedding' } else { Write-Host '[FAIL] bge-m3 embedding'; $failed = $true }
foreach ($service in @(@(15432,'PostgreSQL'), @(6379,'Redis'), @(9000,'RustFS API'), @(9876,'RocketMQ NameServer'))) {
    if (Test-DevPort $service[0]) { Write-Host "[OK] $($service[1]) reachable on $($service[0])" }
    else { Write-Host "[FAILED] $($service[1]) unavailable on $($service[0])"; $failed = $true }
}
if (Test-DevPort 11434) { Write-Host '[OK] Ollama available on 11434' }
else { Write-Host '[WARN] Ollama unavailable (optional)' }
if (Test-DevHttp 'http://127.0.0.1:9090/api/safeguard-agent/' Backend) { Write-Host '[OK] safeguard backend HTTP 200: http://127.0.0.1:9090/api/safeguard-agent' }
else { Write-Host '[FAILED] safeguard backend unavailable'; $failed = $true }
if (Test-DevHttp 'http://127.0.0.1:5173/' Frontend) { Write-Host '[OK] safeguard frontend HTTP 200: http://127.0.0.1:5173' }
else { Write-Host '[FAILED] safeguard frontend unavailable'; $failed = $true }
if ($failed) { throw 'safeguard checks failed; see [FAILED] lines above.' }
