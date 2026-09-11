# PowerShell 5.1-compatible helpers for local startup only.
function ConvertTo-DevArgument {
    param([AllowEmptyString()][string]$Value)
    '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}
function Invoke-DevCommand {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [string]$WorkingDirectory = (Get-Location).Path,
        [ValidateRange(1, 900)][int]$TimeoutSeconds = 30,
        [string]$Label = 'Command',
        [string]$LogPath,
        [switch]$Quiet
    )
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $FilePath
    $info.Arguments = (($ArgumentList | ForEach-Object { ConvertTo-DevArgument $_ }) -join ' ')
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    $output = New-Object System.Text.StringBuilder
    $errors = New-Object System.Text.StringBuilder
    $writer = $null
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $nextNotice = 5
    try {
        if ($LogPath) { $writer = New-Object System.IO.StreamWriter($LogPath, $false) }
        [void]$process.Start()
        # Do not inherit the interactive console handles used by the caller.
        $process.StandardInput.Close()
        $outRead = $process.StandardOutput.ReadLineAsync()
        $errRead = $process.StandardError.ReadLineAsync()
        $outDone = $false
        $errDone = $false
        while (-not ($process.HasExited -and $outDone -and $errDone)) {
            if (-not $outDone -and $outRead.IsCompleted) {
                $line = $outRead.GetAwaiter().GetResult()
                if ($null -eq $line) { $outDone = $true }
                else {
                    [void]$output.AppendLine($line)
                    if ($writer) { $writer.WriteLine($line); $writer.Flush() }
                    if (-not $Quiet) { Write-Host $line }
                    $outRead = $process.StandardOutput.ReadLineAsync()
                }
            }
            if (-not $errDone -and $errRead.IsCompleted) {
                $line = $errRead.GetAwaiter().GetResult()
                if ($null -eq $line) { $errDone = $true }
                else {
                    [void]$errors.AppendLine($line)
                    if ($writer) { $writer.WriteLine($line); $writer.Flush() }
                    if (-not $Quiet) { Write-Host $line }
                    $errRead = $process.StandardError.ReadLineAsync()
                }
            }
            if ($watch.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
                # Only terminate this invocation and its descendants, never containers.
                if (-not $process.HasExited) {
                    & "$env:SystemRoot\System32\taskkill.exe" /PID $process.Id /T /F 2>&1 | Out-Null
                }
                throw "$Label timed out after $($TimeoutSeconds)s. Log: $LogPath"
            }
            if ($watch.Elapsed.TotalSeconds -ge $nextNotice) {
                Write-Host ("[WAIT] {0}: {1}s / {2}s; log: {3}" -f $Label, [int]$watch.Elapsed.TotalSeconds, $TimeoutSeconds, $LogPath)
                $nextNotice += 5
            }
            Start-Sleep -Milliseconds 10
        }
        $process.WaitForExit()
        [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $output.ToString(); Error = $errors.ToString() }
    }
    finally {
        if ($writer) { $writer.Dispose() }
        $process.Dispose()
    }
}
function Test-DevPort {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.ConnectAsync('127.0.0.1', $Port)
        return ($connect.Wait(1000) -and $client.Connected)
    }
    catch { return $false }
    finally { $client.Dispose() }
}
function Test-DevHttp {
    param([string]$Uri, [ValidateSet('Backend','Frontend')][string]$Kind)
    $response = $null
    $reader = $null
    try {
        $request = [System.Net.HttpWebRequest]::Create($Uri)
        $request.Proxy = $null
        $request.Timeout = 2000
        $request.ReadWriteTimeout = 2000
        $response = $request.GetResponse()
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        $body = $reader.ReadToEnd()
        if ([int]$response.StatusCode -ne 200) { return $false }
        if ($Kind -eq 'Frontend') { return ($body -match '<html') }
        # safeguard has no public actuator endpoint; its root returns the auth JSON envelope.
        $json = $body | ConvertFrom-Json
        return ($null -ne $json.PSObject.Properties['code'])
    }
    catch { return $false }
    finally {
        if ($reader) { $reader.Dispose() }
        if ($response) { $response.Close() }
    }
}
function Wait-DevHttp {
    param(
        [string]$Uri, [string]$Kind, [string]$Label,
        [System.Diagnostics.Process]$Process,
        [int]$TimeoutSeconds = 120, [string]$LogPath
    )
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $nextNotice = 0
    $successes = 0
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if ($Process) {
            $Process.Refresh()
            if ($Process.HasExited) {
                throw "$Label exited with code $($Process.ExitCode) before becoming ready. Log: $LogPath"
            }
        }
        if (Test-DevHttp -Uri $Uri -Kind $Kind) {
            $successes++
            if ($successes -ge 2) { Write-Host "[OK] $Label HTTP 200: $Uri"; return }
        }
        else { $successes = 0 }
        if ($watch.Elapsed.TotalSeconds -ge $nextNotice) {
            Write-Host ("[WAIT] {0} HTTP readiness ({1}s / {2}s); log: {3}" -f $Label, [int]$watch.Elapsed.TotalSeconds, $TimeoutSeconds, $LogPath)
            $nextNotice += 5
        }
        Start-Sleep -Milliseconds 500
    }
    throw "$Label HTTP check timed out after $($TimeoutSeconds)s. Log: $LogPath"
}
