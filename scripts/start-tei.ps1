param(
    [string]$ModelPath = 'D:\1-project\RAG_论文\RAG_2\assets\models\bge-m3'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-runtime.ps1')
$docker = (Get-Command docker.exe -ErrorAction Stop).Source
if (-not (Test-Path -LiteralPath $ModelPath)) { throw "TEI model path does not exist: $ModelPath" }
& $docker info --format '{{.ServerVersion}}' | Out-Null

$existing = @(& $docker ps -a --filter "name=^/$($script:TeiContainerName)$" --format '{{.Names}}' 2>$null | Where-Object { $_ })
if ($existing.Count -gt 0) {
    $binding = (& $docker inspect $script:TeiContainerName --format '{{json .HostConfig.PortBindings}}')
    if ($binding -notmatch '18083') {
        throw "Existing container $($script:TeiContainerName) has a non-18083 host mapping: $binding. Remove/recreate that exact stopped container before retrying."
    }
    $state = (& $docker inspect $script:TeiContainerName --format '{{.State.Status}}').Trim()
    if ($state -ne 'running') { & $docker start $script:TeiContainerName | Out-Null }
}
else {
    & $docker run -d --gpus all --restart unless-stopped --name $script:TeiContainerName -p '18083:80' -v "${ModelPath}:/data/bge-m3" ghcr.io/huggingface/text-embeddings-inference:120-1.9 --model-id /data/bge-m3 --served-model-name bge-m3 --max-client-batch-size 4096 | Out-Null
}
Wait-TeiReady
