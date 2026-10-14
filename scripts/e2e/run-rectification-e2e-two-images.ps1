param(
  [string]$BaseUrl = 'http://127.0.0.1:28080',
  [Parameter(Mandatory = $true)][string]$BeforeImagePath,
  [Parameter(Mandatory = $true)][string]$AfterImagePath,
  [string]$Username = 'admin',
  [string]$Password = $env:SAFEGUARD_E2E_PASSWORD
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Password)) {
  throw 'Set SAFEGUARD_E2E_PASSWORD or pass -Password.'
}
$base = $BaseUrl.TrimEnd('/')
$runId = 'e2e-two-images-' + (Get-Date -Format 'yyyyMMddHHmmss')
function Json($value) { $value | ConvertTo-Json -Depth 20 -Compress }
function Api($method, $path, $body, $token) {
  $params = @{ Uri = ($base + $path); Method = $method; Headers = @{ Authorization = "Bearer $token" }; TimeoutSec = 180; UseBasicParsing = $true }
  if ($null -ne $body) { $params.ContentType = 'application/json'; $params.Body = (Json $body) }
  return Invoke-RestMethod @params
}
function Assert($condition, $message) { if (-not $condition) { throw $message } }
$login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body (Json @{ username=$Username; password=$Password }) -TimeoutSec 30
$token = $login.data.accessToken
Assert (-not [string]::IsNullOrWhiteSpace($token)) '登录未返回 token'
$beforeBytes = [IO.File]::ReadAllBytes($beforeImagePath)
$afterBytes = [IO.File]::ReadAllBytes($afterImagePath)
$beforeDataUri = 'data:image/png;base64,' + [Convert]::ToBase64String($beforeBytes)
$afterDataUri = 'data:image/png;base64,' + [Convert]::ToBase64String($afterBytes)
$created = (Api 'Post' '/api/mini/pingan/three-checks/quick-shot/records' @{ companyId=4; departmentId=101109; teamId=1011001; ownerUserId=2; businessDate=(Get-Date -Format 'yyyy-MM-dd'); sourceRecordId=$runId; clientRequestId=$runId; payload=@{ hazardDescription='真实前后图片 E2E：现场防护整改复核'; location='E2E 测试现场'; photo=$beforeDataUri } } $token).data
$approved = (Api 'Post' "/api/mini/pingan/three-checks/quick-shot/records/$($created.id)/workflow-actions" @{ action='APPROVE'; version=$created.version; payload=@{} } $token).data
$orders = (Api 'Get' "/api/pingan/hazard-rectification/orders?sourceType=QUICK_SHOT&sourceRecordId=$($created.id)" $null $token).data
$order = $orders.items[0]
Assert ($null -ne $order) '未找到 APPROVE 创建的整改工单'
$issued = (Api 'Post' "/api/pingan/hazard-rectification/orders/$($order.id)/actions" @{ action='ISSUE_RECTIFICATION'; version=$order.version; payload=@{ rectificationResponsibleUserId=2; rectificationDepartmentId=101109; acceptanceUserId=3; rectificationRequirement='根据真实前后图片完成整改并复核'; rectificationDeadline=(Get-Date).AddDays(1).ToString('yyyy-MM-dd HH:mm:ss') } } $token).data
$uploaded = Invoke-RestMethod -Uri "$base/api/pingan/hazard-rectification/orders/$($order.id)/attachments?fileKind=RECTIFICATION_AFTER_PHOTO" -Method Post -Headers @{ Authorization = "Bearer $token" } -Form @{ file = Get-Item $afterImagePath } -TimeoutSec 60
$rectified = (Api 'Post' "/api/pingan/hazard-rectification/orders/$($order.id)/actions" @{ action='MARK_RECTIFIED'; version=$issued.version; payload=@{ rectificationDescription='真实整改后图片已上传，完成整改'; afterPhoto=$uploaded.data.url } } $token).data
$review = (Api 'Post' "/api/pingan/hazard-rectification/orders/$($order.id)/agent-review" @{ orderVersion=$rectified.version; criteria=@('整改后现场防护措施完整且无明显缺失'); beforeEvidence=@('整改前现场图片'); afterEvidence=@('整改后现场图片'); rectificationDescription='真实整改后图片已上传，完成整改'; beforeImageData=@($beforeDataUri); afterImageData=@($afterDataUri) } $token).data
$pending = (Api 'Post' "/api/pingan/hazard-rectification/orders/$($order.id)/actions" @{ action='REQUEST_ACCEPTANCE'; version=$rectified.version; payload=@{ acceptanceUserId=3; acceptanceRemark='已查看 Agent 前后图片复核建议，提交人工验收' } } $token).data
$closed = (Api 'Post' "/api/pingan/hazard-rectification/orders/$($order.id)/actions" @{ action='ACCEPT'; version=$pending.version; payload=@{ acceptanceUserId=3; acceptanceRemark='人工验收通过' } } $token).data
Start-Sleep -Seconds 4
$summary = (Api 'Get' "/api/pingan/hazard-rectification/orders/$($order.id)/agent-summary" $null $token).data
if ($null -eq $summary) { $summary = (Api 'Post' "/api/pingan/hazard-rectification/orders/$($order.id)/agent-summary" @{ orderVersion=$closed.version; hazardDescription='真实前后图片 E2E 隐患'; rectificationDescription='真实整改后图片已上传，完成整改'; acceptanceResult='PASS'; acceptanceRemark='人工验收通过'; measures=@('完成现场整改'); acceptanceCriteria=@('整改后现场防护措施完整且无明显缺失'); flowFacts=@('CLOSED'); citations=@('e2e-two-images') } $token).data }
$editedJson = Json @{ summaryId=$summary.id; orderId=$order.id; source='real-two-images-e2e'; conclusion='人工修订确认：整改完成并验收通过'; }
$revised = (Api 'Post' "/api/pingan/hazard-rectification/orders/$($order.id)/agent-summary/edit" @{ summaryVersion=$summary.summaryVersion; orderVersion=$closed.version; editedJson=$editedJson; editReason='E2E 人工修订' } $token).data
$confirmed = (Api 'Post' "/api/pingan/hazard-rectification/orders/$($order.id)/agent-summary/confirm" @{ summaryVersion=$revised.summaryVersion; orderVersion=$closed.version; confirmedJson=$editedJson } $token).data
$audit = (Api 'Get' "/api/pingan/hazard-rectification/orders/$($order.id)/agent-summary" $null $token).data
Write-Output (Json @{ runId=$runId; orderId=$order.id; beforeBytes=$beforeBytes.Length; afterBytes=$afterBytes.Length; statuses=@($approved.status,$issued.status,$rectified.status,$review.status,$pending.status,$closed.status); reviewSuggestion=$review.overallSuggestion; reviewStatus=$review.status; summaryStatus=$audit.status; summaryVersion=$audit.summaryVersion; sourceDigest=$audit.sourceDigest })
