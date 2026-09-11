# Shared local runtime ownership and TEI readiness checks.
$script:TeiContainerName = 'tei-bge-m3'
$script:TeiHostPort = 18083
$script:RocketMqHostPorts = @(9876, 10909, 10911, 10912, 18080, 18081, 18082)

function Get-DockerPortOwners {
    param([int]$Port)
    $docker = Get-Command docker.exe -ErrorAction Stop
    @(& $docker.Source ps --filter "publish=$Port" --format '{{.Names}}' 2>$null | Where-Object { $_ })
}

function Assert-LocalRuntimePortOwnership {
    $checks = @(
        @{ Port = $script:TeiHostPort; Expected = $script:TeiContainerName; Label = 'TEI' },
        @{ Port = 9876; Expected = 'rmqnamesrv'; Label = 'RocketMQ NameServer' },
        @{ Port = 10909; Expected = 'rmqbroker'; Label = 'RocketMQ broker 10909' },
        @{ Port = 10911; Expected = 'rmqbroker'; Label = 'RocketMQ broker 10911' },
        @{ Port = 10912; Expected = 'rmqbroker'; Label = 'RocketMQ broker 10912' },
        @{ Port = 18080; Expected = 'rmqbroker'; Label = 'RocketMQ proxy 18080' },
        @{ Port = 18081; Expected = 'rmqbroker'; Label = 'RocketMQ proxy 18081' },
        @{ Port = 18082; Expected = 'rmqbroker'; Label = 'RocketMQ proxy 18082' }
    )
    foreach ($check in $checks) {
        $owners = @(Get-DockerPortOwners $check.Port)
        $unexpected = @($owners | Where-Object { $_ -ne $check.Expected })
        if ($unexpected.Count -gt 0) {
            throw "Port $($check.Port) expected $($check.Expected) for $($check.Label), actual container(s): $($unexpected -join ', ')"
        }
        $listeners = @(Get-NetTCPConnection -LocalPort $check.Port -State Listen -ErrorAction SilentlyContinue)
        foreach ($listener in $listeners) {
            $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)" -ErrorAction SilentlyContinue
            if ($check.Port -eq $script:TeiHostPort -and $owners -notcontains $script:TeiContainerName) {
                throw "Port $($check.Port) expected TEI container $($script:TeiContainerName), actual process PID $($listener.OwningProcess): $($process.CommandLine)"
            }
            if ($check.Port -ne $script:TeiHostPort -and $owners -notcontains 'rmqbroker') {
                throw "Port $($check.Port) expected RocketMQ container rmqbroker, actual process PID $($listener.OwningProcess): $($process.CommandLine)"
            }
        }
    }
    if ((Get-DockerPortOwners $script:TeiHostPort) -contains 'rmqbroker') {
        throw 'Port 18083 is reserved for TEI and must not be published by RocketMQ.'
    }
}

function Test-TeiEmbeddingSmoke {
    try {
        $body = @{ model = 'bge-m3'; input = @('施工现场临边应设置防护栏杆。') } | ConvertTo-Json -Compress
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$($script:TeiHostPort)/v1/embeddings" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 15
        $vector = @($response.data[0].embedding)
        if ($vector.Count -le 0) { return $false }
        return (@($vector | Where-Object { [double]$_ -ne 0 -and -not [double]::IsNaN([double]$_) -and -not [double]::IsInfinity([double]$_) }).Count -gt 0)
    }
    catch { return $false }
}

function Wait-TeiReady {
    param([int]$TimeoutSeconds = 120)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $healthy = $false
        try { $healthy = ((Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($script:TeiHostPort)/health" -TimeoutSec 3).StatusCode -eq 200) } catch {}
        if ($healthy -and (Test-TeiEmbeddingSmoke)) { Write-Host "[OK] bge-m3 embedding smoke on 18083"; return }
        Start-Sleep -Seconds 2
    }
    throw "TEI readiness failed on port $($script:TeiHostPort): health or bge-m3 embedding smoke unavailable"
}
