[CmdletBinding()]
param(
    [ValidateSet('static', 'retrieval-unit', 'hosted', 'deterministic', 'legal-live', 'phase61', 'full')]
    [string]$Suite = 'deterministic',
    [string]$BaseUrl = 'http://127.0.0.1:9090/api/safeguard-agent',
    [string]$ServiceToken = 'local-eval-token',
    [string]$LegalOutput = 'outputs/legal-evaluation-v0.1/matrix-current.json',
    [switch]$PlanOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$startedAt = Get-Date
$results = [System.Collections.Generic.List[object]]::new()

function Add-StepResult {
    param([string]$Id, [string]$Status, [int]$ExitCode, [double]$ElapsedSeconds, [string]$Detail)
    $results.Add([ordered]@{
        id = $Id
        status = $Status
        exitCode = $ExitCode
        elapsedSeconds = [Math]::Round($ElapsedSeconds, 2)
        detail = $Detail
    })
}

function Invoke-Step {
    param([string]$Id, [string]$Description, [scriptblock]$Action)
    Write-Host "[$Id] $Description"
    if ($PlanOnly) {
        Add-StepResult -Id $Id -Status 'PLANNED' -ExitCode 0 -ElapsedSeconds 0 -Detail $Description
        return
    }
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        & $Action
        $code = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
        if ($code -ne 0) { throw "exit code $code" }
        $watch.Stop()
        Add-StepResult -Id $Id -Status 'PASS' -ExitCode 0 -ElapsedSeconds $watch.Elapsed.TotalSeconds -Detail $Description
    }
    catch {
        $watch.Stop()
        Add-StepResult -Id $Id -Status 'FAIL' -ExitCode 1 -ElapsedSeconds $watch.Elapsed.TotalSeconds -Detail $_.Exception.Message
        throw
    }
}

function Invoke-MavenTests {
    param([string]$Tests)
    & cmd.exe /d /c "mvnw.cmd -q -pl rag -am -Dtest=$Tests -Dsurefire.failIfNoSpecifiedTests=false test"
}

function Invoke-StaticSuite {
    Invoke-Step 'STATIC-MATRIX' 'Parse evaluation-matrix.json and verify referenced assets.' {
        $matrixPath = Join-Path $repoRoot 'evaluation-matrix.json'
        $matrix = Get-Content -Raw -LiteralPath $matrixPath | ConvertFrom-Json
        if ($matrix.schemaVersion -ne 1) { throw 'Unsupported evaluation matrix schema.' }
        foreach ($asset in @($matrix.dataset.legalManifest, $matrix.dataset.visualManifest)) {
            if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $asset))) { throw "Missing asset: $asset" }
        }
        foreach ($baseline in $matrix.baselines.PSObject.Properties.Value) {
            if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $baseline.report))) { throw "Missing baseline: $($baseline.report)" }
        }
    }
    Invoke-Step 'STATIC-PYTHON' 'Compile the legal evaluation runner.' {
        & python -m py_compile (Join-Path $repoRoot 'scripts/evaluate_legal_golden.py')
    }
    Invoke-Step 'STATIC-GOLDEN' 'Verify the legal golden manifest has exactly 37 uniquely identified cases.' {
        $script = @'
import pathlib, sys, yaml
p = pathlib.Path(sys.argv[1])
d = yaml.safe_load(p.read_text(encoding="utf-8"))
cases = d.get("cases", [])
ids = [c.get("case_id") for c in cases]
assert len(cases) == 37, f"expected 37 cases, got {len(cases)}"
assert len(ids) == len(set(ids)), "duplicate case_id"
print(f"legal golden cases: {len(cases)}")
'@
        & python -c $script (Join-Path $repoRoot 'rag/src/test/resources/fixtures/legal-retrieval/golden-set-v0.1-seed.yaml')
    }
}

function Invoke-RetrievalUnitSuite {
    Invoke-Step 'RET-UNIT' 'Run retrieval channel, scope, ranking, fusion, rerank, lexical coverage, and evidence-gate tests.' {
        Invoke-MavenTests 'RetrievalEngineTest,MultiChannelRetrievalEngineTest,KeywordSearchChannelTest,VectorSearchChannelTest,RetrievalScopeResolverTest,ScopeQuotaTest,ChunkRankingTest,DeduplicationPostProcessorTest,LexicalCoveragePostProcessorTest,EvidenceGatePostProcessorTest'
    }
}

function Invoke-HostedSuite {
    Invoke-Step 'HOSTED-CONTRACT' 'Run hosted service and deterministic HTTP integration tests.' {
        Invoke-MavenTests 'HostedHazardServiceTest,HostedHazardControllerIntegrationTest'
    }
}

function Invoke-QualityUnitSuite {
    Invoke-Step 'QUALITY-UNIT' 'Run evidence-selection, visual query, visual service, and visual runner tests.' {
        Invoke-MavenTests 'LegalAnswerEvidenceSelectionTest,VisualLegalQueryBuilderTest,VisualHazardServiceTest,VisualEvaluationRunnerTest,HazardAssessmentServiceTest'
    }
}

function Invoke-LegalLiveSuite {
    Invoke-Step 'LEGAL-LIVE' 'Run all 37 legal cases against the currently active retrieval configuration.' {
        & python (Join-Path $repoRoot 'scripts/evaluate_legal_golden.py') --base-url $BaseUrl --token $ServiceToken --output $LegalOutput
    }
    Invoke-Step 'LEGAL-GATE' 'Apply the current hybrid non-regression gate.' {
        $report = Get-Content -Raw -LiteralPath (Join-Path $repoRoot $LegalOutput) | ConvertFrom-Json
        $metrics = $report.metrics
        if ($report.caseCount -ne 37) { throw "Expected 37 cases, got $($report.caseCount)." }
        if ($report.errorCaseCount -ne 0) { throw "Evaluation contains $($report.errorCaseCount) errors." }
        if ($metrics.finalEvidenceHitRate -lt 0.4571428571) { throw "Final evidence hit rate regressed: $($metrics.finalEvidenceHitRate)." }
        if ($metrics.compoundAllEvidenceGroupsHitRate -lt 0.5) { throw "Compound evidence coverage regressed: $($metrics.compoundAllEvidenceGroupsHitRate)." }
        if ($metrics.unanswerableFalseCitationRate -gt 0) { throw "Unanswerable false citation rate is non-zero: $($metrics.unanswerableFalseCitationRate)." }
        if ($metrics.stageHitRates.RAW_RECALL -lt 0.6857142857) { throw "RAW_RECALL stage hit rate regressed: $($metrics.stageHitRates.RAW_RECALL)." }
    }
}

function Invoke-Phase61Preflight {
    Invoke-Step 'PHASE61-PREFLIGHT' 'Verify Agent and safe-flow endpoints are reachable before the real hosted loop.' {
        $agent = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 -Uri 'http://127.0.0.1:9090/api/safeguard-agent/actuator/health'
        $safeFlow = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 -Uri 'http://127.0.0.1:28080/actuator/health'
        if ($agent.StatusCode -ne 200 -or $safeFlow.StatusCode -ne 200) { throw 'One or more hosted services are unhealthy.' }
    }
    Write-Warning 'Phase 61 business-sample execution is intentionally not automated here: it requires an explicit safe-flow record/image and must keep approval/writeback disabled.'
}

Push-Location $repoRoot
try {
    switch ($Suite) {
        'static' { Invoke-StaticSuite }
        'retrieval-unit' { Invoke-RetrievalUnitSuite }
        'hosted' { Invoke-HostedSuite }
        'deterministic' { Invoke-StaticSuite; Invoke-HostedSuite; Invoke-RetrievalUnitSuite; Invoke-QualityUnitSuite }
        'legal-live' { Invoke-LegalLiveSuite }
        'phase61' { Invoke-Phase61Preflight }
        'full' { Invoke-StaticSuite; Invoke-HostedSuite; Invoke-RetrievalUnitSuite; Invoke-QualityUnitSuite; Invoke-LegalLiveSuite; Invoke-Phase61Preflight }
    }
}
finally {
    Pop-Location
    $finishedAt = Get-Date
    $summary = [ordered]@{
        suite = $Suite
        planOnly = [bool]$PlanOnly
        startedAt = $startedAt.ToString('o')
        finishedAt = $finishedAt.ToString('o')
        passed = @($results | Where-Object status -eq 'PASS').Count
        failed = @($results | Where-Object status -eq 'FAIL').Count
        planned = @($results | Where-Object status -eq 'PLANNED').Count
        results = $results
    }
    $summaryDir = Join-Path $repoRoot 'outputs/evaluation-matrix'
    New-Item -ItemType Directory -Force -Path $summaryDir | Out-Null
    $summaryPath = Join-Path $summaryDir ("{0}-{1}.json" -f $Suite, (Get-Date -Format 'yyyyMMdd-HHmmss'))
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Write-Host "Evaluation summary: $summaryPath"
}
