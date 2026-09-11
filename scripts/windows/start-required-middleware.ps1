param(
    [string]$PostgresPassword = "123456",
    [int]$PostgresHostPort = 15432,
    [string]$RustFsAccessKey = "rustfsadmin",
    [string]$RustFsSecretKey = "rustfsadmin",
    [string]$PostgresImage = "registry-1.docker.io/pgvector/pgvector:pg16",
    [string]$RustFsImage = "registry-1.docker.io/rustfs/rustfs:1.0.0-alpha.72"
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot '..\dev-runtime.ps1')

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found. Please install it or add it to PATH."
    }
}

function Assert-DockerReady {
    Write-Host '[STEP] Docker Desktop check (timeout 30s)'
    $result = Invoke-DevCommand -FilePath (Get-Command docker.exe).Source -ArgumentList @('info','--format','{{.ServerVersion}}') -TimeoutSeconds 30 -Label 'Docker Desktop check' -Quiet
    if ($result.ExitCode -ne 0) {
        throw "Docker is installed but the daemon is not running. Start Docker Desktop, then rerun this script."
    }
}

function Invoke-Docker {
    param([string[]]$DockerArgs)
    $result = Invoke-DevCommand -FilePath (Get-Command docker.exe).Source -ArgumentList $DockerArgs -TimeoutSeconds 180 -Label "Docker $($DockerArgs[0])" -Quiet
    if ($result.ExitCode -ne 0) { throw "Docker $($DockerArgs[0]) failed: $($result.Error)" }
    $result.Output.TrimEnd()
}

function Start-Or-CreatePostgres {
    $existing = Invoke-Docker -DockerArgs @('ps','-a','--filter','name=^/safeguard-postgres$','--format','{{.Names}}')
    if ($existing -eq "safeguard-postgres") {
        Invoke-Docker -DockerArgs @("start", "safeguard-postgres") | Out-Null
        Write-Host "PostgreSQL container safeguard-postgres is running."
        return
    }

    Invoke-Docker -DockerArgs @(
        "run", "-d",
        "--name", "safeguard-postgres",
        "-e", "POSTGRES_DB=safeguard",
        "-e", "POSTGRES_USER=postgres",
        "-e", "POSTGRES_PASSWORD=$PostgresPassword",
        "-p", "${PostgresHostPort}:5432",
        "-v", "safeguard-pgdata:/var/lib/postgresql/data",
        $PostgresImage
    ) | Out-Null

    Write-Host "PostgreSQL container safeguard-postgres was created."
}

function Start-Or-CreateRustFs {
    $existing = Invoke-Docker -DockerArgs @('ps','-a','--filter','name=^/safeguard-rustfs$','--format','{{.Names}}')
    if ($existing -eq "safeguard-rustfs") {
        Invoke-Docker -DockerArgs @("start", "safeguard-rustfs") | Out-Null
        Write-Host "RustFS container safeguard-rustfs is running."
        return
    }

    Invoke-Docker -DockerArgs @(
        "run", "-d",
        "--name", "safeguard-rustfs",
        "-p", "9000:9000",
        "-p", "9001:9001",
        "-v", "safeguard-rustfs-data:/data",
        "-e", "RUSTFS_ACCESS_KEY=$RustFsAccessKey",
        "-e", "RUSTFS_SECRET_KEY=$RustFsSecretKey",
        "-e", "RUSTFS_CONSOLE_ENABLE=true",
        $RustFsImage,
        "--address", ":9000",
        "--console-enable",
        "--access-key", $RustFsAccessKey,
        "--secret-key", $RustFsSecretKey,
        "/data"
    ) | Out-Null

    Write-Host "RustFS container safeguard-rustfs was created."
}

Assert-Command docker
Assert-DockerReady
Start-Or-CreatePostgres
Start-Or-CreateRustFs

Write-Host ""
Write-Host "Required local middleware is ready:"
Write-Host "- PostgreSQL: jdbc:postgresql://127.0.0.1:${PostgresHostPort}/safeguard?client_encoding=UTF8"
Write-Host "- RustFS API: http://localhost:9000"
Write-Host "- RustFS Console: http://localhost:9001"
