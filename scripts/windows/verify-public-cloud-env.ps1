$ErrorActionPreference = "Continue"

function Test-Port {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$Name
    )

    $result = Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue
    if ($result.TcpTestSucceeded) {
        Write-Host "[OK] $Name reachable at ${HostName}:${Port}"
    }
    else {
        Write-Host "[FAIL] $Name not reachable at ${HostName}:${Port}"
    }
}

Write-Host "Checking local runtime commands..."
foreach ($command in @("java", "mvn", "docker")) {
    if (Get-Command $command -ErrorAction SilentlyContinue) {
        Write-Host "[OK] $command is on PATH"
    }
    else {
        Write-Host "[WARN] $command is not on PATH"
    }
}

Write-Host ""
Write-Host "Checking local required middleware..."
Test-Port -HostName "127.0.0.1" -Port 15432 -Name "PostgreSQL"
Test-Port -HostName "127.0.0.1" -Port 9000 -Name "RustFS API"
Test-Port -HostName "127.0.0.1" -Port 9001 -Name "RustFS Console"

Write-Host ""
Write-Host "Checking public-cloud middleware variables..."
$redisHost = if ($env:SAFEGUARD_REDIS_HOST) { $env:SAFEGUARD_REDIS_HOST } else { "common-redis-dev.magestack.cn" }
$redisPort = if ($env:SAFEGUARD_REDIS_PORT) { [int]$env:SAFEGUARD_REDIS_PORT } else { 19389 }
$rocketMq = if ($env:SAFEGUARD_ROCKETMQ_NAME_SERVER) { $env:SAFEGUARD_ROCKETMQ_NAME_SERVER } else { "common-rocketmq-dev.magestack.cn:9876" }

Test-Port -HostName $redisHost -Port $redisPort -Name "Redis"

$rocketParts = $rocketMq.Split(":")
if ($rocketParts.Length -eq 2) {
    Test-Port -HostName $rocketParts[0] -Port ([int]$rocketParts[1]) -Name "RocketMQ NameServer"
}
else {
    Write-Host "[WARN] SAFEGUARD_ROCKETMQ_NAME_SERVER should look like host:port"
}

Write-Host ""
Write-Host "Checking AI API key variables..."
$applicationYaml = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")) "bootstrap\src\main\resources\application.yaml"
foreach ($key in @("BAILIAN_API_KEY", "SILICONFLOW_API_KEY")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key))) {
        $configuredInYaml = $false
        if (Test-Path -LiteralPath $applicationYaml) {
            $configuredInYaml = Select-String -Path $applicationYaml -Pattern "\$\{$key`:sk-" -Quiet
        }
        if ($configuredInYaml) {
            Write-Host "[OK] $key has an application.yaml fallback"
        }
        else {
            Write-Host "[WARN] $key is not set"
        }
    }
    else {
        Write-Host "[OK] $key is set"
    }
}
