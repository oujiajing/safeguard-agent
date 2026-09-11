param(
    [string]$ContainerName = "safeguard-postgres",
    [string]$Database = "safeguard",
    [string]$User = "postgres"
)

$ErrorActionPreference = "Stop"

function Invoke-Docker {
    param([string[]]$DockerArgs)
    & docker @DockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($DockerArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$schemaPath = Join-Path $repoRoot "resources\database\schema_pg.sql"
$dataPath = Join-Path $repoRoot "resources\database\init_data_pg.sql"

if (-not (Test-Path -LiteralPath $schemaPath)) {
    throw "Missing schema file: $schemaPath"
}
if (-not (Test-Path -LiteralPath $dataPath)) {
    throw "Missing init data file: $dataPath"
}

docker inspect $ContainerName *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Container '$ContainerName' does not exist. Run scripts\windows\start-required-middleware.ps1 first."
}

Invoke-Docker -DockerArgs @("start", $ContainerName) | Out-Null

Write-Host "Waiting for PostgreSQL to accept connections..."
for ($i = 1; $i -le 30; $i++) {
    docker exec $ContainerName pg_isready -U $User -d $Database *> $null
    if ($LASTEXITCODE -eq 0) {
        break
    }
    if ($i -eq 30) {
        throw "PostgreSQL is not ready after waiting."
    }
    Start-Sleep -Seconds 2
}

Invoke-Docker -DockerArgs @("cp", $schemaPath, "${ContainerName}:/tmp/schema_pg.sql")
Invoke-Docker -DockerArgs @("cp", $dataPath, "${ContainerName}:/tmp/init_data_pg.sql")

Write-Host "Applying schema_pg.sql..."
Invoke-Docker -DockerArgs @("exec", $ContainerName, "psql", "-U", $User, "-d", $Database, "-v", "ON_ERROR_STOP=1", "-f", "/tmp/schema_pg.sql")

Write-Host "Applying init_data_pg.sql..."
Invoke-Docker -DockerArgs @("exec", $ContainerName, "psql", "-U", $User, "-d", $Database, "-v", "ON_ERROR_STOP=1", "-f", "/tmp/init_data_pg.sql")

Write-Host "PostgreSQL initialization completed."
