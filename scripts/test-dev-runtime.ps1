param([AllowEmptyString()][string]$Echo)
if ($PSBoundParameters.ContainsKey('Echo')) { [Console]::Out.WriteLine($Echo); exit 0 }
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-runtime.ps1')
$windowsPowerShell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
function Assert-Dev([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "[FAILED] $Name" }
    Write-Host "[PASS] $Name"
}
function Invoke-TestChild([string]$Code, [int]$TimeoutSeconds = 5) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Code))
    Invoke-DevCommand -FilePath $windowsPowerShell -ArgumentList @('-NoProfile','-NonInteractive','-EncodedCommand',$encoded) -TimeoutSeconds $TimeoutSeconds -Quiet -Label 'test child'
}
$r = Invoke-TestChild '[Console]::Out.WriteLine([Console]::In.ReadToEnd().Length)'
Assert-Dev ($r.Output.Trim() -eq '0' -and $r.ExitCode -eq 0) 'stdin is closed; console input cannot block child'
$r = Invoke-TestChild '[Console]::Out.WriteLine("x" * 131072); [Console]::Error.WriteLine("y" * 131072); exit 23'
Assert-Dev ($r.ExitCode -eq 23 -and $r.Output.Length -gt 131072 -and $r.Error.Length -gt 131072) 'stdout/stderr drain independently and nonzero exit preserved'
foreach ($value in @('a b', 'quote"value', 'C:\space dir\', '')) {
    $r = Invoke-DevCommand -FilePath $windowsPowerShell -ArgumentList @('-NoProfile','-NonInteractive','-File',$PSCommandPath,'-Echo',$value) -Quiet
    Assert-Dev ($r.ExitCode -eq 0 -and $r.Output.TrimEnd([char[]]@(13,10)) -ceq $value) "native argument round trip: $value"
}
$timer = [Diagnostics.Stopwatch]::StartNew()
$timedOut = $false
try { $null = Invoke-TestChild 'Start-Sleep -Seconds 30' 1 }
catch { $timedOut = $_.Exception.Message -match 'timed out' }
Assert-Dev ($timedOut -and $timer.Elapsed.TotalSeconds -lt 5) 'hung child fails within timeout'
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
try {
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    Assert-Dev (Test-DevPort $port) 'open TCP port detected'
    Assert-Dev (-not (Test-DevHttp "http://127.0.0.1:$port/" Backend)) 'TCP listener without HTTP is not backend-ready'
}
finally { $listener.Stop() }
Assert-Dev (-not (Test-DevPort $port)) 'closed TCP port detected'
$child = Start-Process $windowsPowerShell -ArgumentList @('-NoProfile','-NonInteractive','-Command','exit 23') -WindowStyle Hidden -PassThru
$child.WaitForExit()
$earlyExit = $false
try { Wait-DevHttp -Uri "http://127.0.0.1:$port/" -Kind Backend -Label 'exited backend' -Process $child -TimeoutSeconds 5 }
catch { $earlyExit = $_.Exception.Message -match 'exited with code 23' }
Assert-Dev $earlyExit 'startup detects early process exit instead of reporting success'
Write-Host '[OK] Startup runtime regression tests passed'
