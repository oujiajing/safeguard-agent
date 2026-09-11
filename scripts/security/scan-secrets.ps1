[CmdletBinding()]
param(
    [string]$Repository
)

$ErrorActionPreference = 'Stop'
$scriptRoot = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
if ([string]::IsNullOrWhiteSpace($Repository)) {
    $Repository = (Resolve-Path (Join-Path $scriptRoot '..\..')).Path
}
$patterns = @(
    '(?i)(api[_-]?key|secret|password|passwd|token|authorization|access[_-]?key|private[_-]?key)\s*[:=]\s*[^\s#]+',
    '(?i)Bearer\s+[A-Za-z0-9._-]{20,}',
    '-----BEGIN [A-Z ]*PRIVATE KEY-----',
    '(?i)(postgres(?:ql)?|redis|mongodb)://[^\s]+'
)
$excludedDirectories = @('.git', 'node_modules', 'target', '.mvn')
$textExtensions = @('.env', '.example', '.yaml', '.yml', '.properties', '.json', '.xml', '.conf', '.ini', '.ps1', '.sh', '.cmd', '.bat', '.md', '.sql', '.java', '.kt', '.ts', '.tsx', '.js', '.jsx', '.py', '.toml', '.txt')
$repositoryFullPath = (Resolve-Path -LiteralPath $Repository).Path.TrimEnd('\\')

function Get-RelativePath([string]$Path) {
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    return $resolved.Substring($repositoryFullPath.Length).TrimStart('\\')
}

function Test-Excluded([string]$Path) {
    foreach ($directory in $excludedDirectories) {
        if ($Path -split '[\\/]' -contains $directory) { return $true }
    }
    return $false
}

$fileList = Get-ChildItem -LiteralPath $Repository -Recurse -File -Force |
    Where-Object {
        -not (Test-Excluded $_.FullName) -and
        $_.Length -le 5242880 -and
        ($textExtensions -contains $_.Extension.ToLowerInvariant() -or $_.Name -match '^(?i)\.env(\..*)?$')
    }
$files = $fileList
$findings = @()
$confirmed = @()
$rg = Get-Command rg -ErrorAction SilentlyContinue
if ($rg) {
    $combinedPattern = ($patterns -join '|')
    $rgOutput = & $rg.Source -n -I --with-filename --hidden --glob '!.git' --glob '!.git/**' --glob '!node_modules/**' --glob '!**/target/**' --glob '!.mvn/**' --glob '!scripts/security/scan-secrets.ps1' --glob '!*.png' --glob '!*.jpg' --glob '!*.jpeg' --glob '!*.gif' --glob '!*.ico' --glob '!*.jar' --glob '!*.woff2' -e $combinedPattern $Repository 2>$null
    foreach ($match in $rgOutput) {
        if ($match -match '^(.*):(\d+):(.*)$') {
            $findings += [pscustomobject]@{
                File = $Matches[1].Substring($Repository.Length).TrimStart('\\')
                Line = [int]$Matches[2]
                Rule = 'PATTERN_MATCH'
            }
        }
    }
} else {
    foreach ($file in $files) {
        try {
            $lineNumber = 0
            foreach ($line in [IO.File]::ReadLines($file.FullName)) {
                $lineNumber++
                foreach ($pattern in $patterns) {
                    if ($line -match $pattern) {
                        $findings += [pscustomobject]@{ File = Get-RelativePath $file.FullName; Line = $lineNumber; Rule = $pattern }
                        break
                    }
                }
            }
        } catch {
            $findings += [pscustomobject]@{ File = Get-RelativePath $file.FullName; Line = 0; Rule = 'SCAN_ERROR' }
        }
    }
}

$configFiles = $fileList | Where-Object { @('.env', '.example', '.yaml', '.yml', '.properties', '.json', '.conf', '.ini', '.toml') -contains $_.Extension.ToLowerInvariant() -or $_.Name -match '^(?i)\.env(\..*)?$' }
foreach ($file in $configFiles) {
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadLines($file.FullName)) {
    $lineNumber++
        if ($line -match '\$\{') { continue }
        if ($line -match '(?i)(api[_-]?key|secret(?:[_-]?access)?[_-]?key|password|passwd|token|authorization|access[_-]?key|private[_-]?key)\s*[:=]\s*(?<value>[^#]*)') {
            $value = $Matches['value'].Trim().Trim('"').Trim("'").Trim()
            if ($value -and $value -notmatch '(?i)(\$\{|\$env:|<[^>]+>|your[-_ ]|replace[-_ ]|changeme|change-me|dummy|example|test|none|null|\[.*\])') {
                $confirmed += [pscustomobject]@{ File = Get-RelativePath $file.FullName; Line = $lineNumber }
            }
        }
    }
}

Write-Output 'Secret scan summary (matched content is intentionally not printed).'
Write-Output ("Files scanned: {0}" -f $files.Count)
Write-Output ("Lexical matches: {0}" -f $findings.Count)
Write-Output ("Confirmed non-empty configuration candidates: {0}" -f $confirmed.Count)
if ($confirmed.Count -gt 0) {
    Write-Output 'Confirmed candidate locations:'
    $confirmed | Sort-Object File,Line | Format-Table -AutoSize
    exit 2
}
exit 0
