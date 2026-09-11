$ErrorActionPreference = 'Continue'

function Stop-LocalProcessOnPort {
    param([int]$Port, [string]$Name, [string[]]$CommandPatterns)
    foreach ($connection in @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Sort-Object OwningProcess -Unique)) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($connection.OwningProcess)" -ErrorAction SilentlyContinue
        if ($CommandPatterns | Where-Object { [string]$process.CommandLine -like "*$($_)*" }) {
            Stop-Process $connection.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-Host "[OK] Stopped $Name (PID $($connection.OwningProcess))"
        }
        else { Write-Host "[WARN] Port $Port belongs to another process; left untouched" }
    }
}

Stop-LocalProcessOnPort 9090 'safeguard backend' @('safeguard', 'bootstrap', 'spring-boot.run.profiles=local')
Stop-LocalProcessOnPort 5173 'safeguard frontend' @('frontend', 'vite')

$docker = Get-Command docker.exe -ErrorAction SilentlyContinue
if ($docker) {
    $tei = @(& $docker.Source ps --filter 'name=^/tei-bge-m3$' --format '{{.Names}}' 2>$null | Where-Object { $_ })
    if ($tei -contains 'tei-bge-m3') {
        & $docker.Source stop tei-bge-m3 | Out-Null
        Write-Host '[OK] Stopped TEI container tei-bge-m3'
    }
}

Write-Host '[OK] Docker volumes and database data were not removed'
