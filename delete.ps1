param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$StateFile = ".lab4-demo-state.json",
    [int]$KafkaWaitSeconds = 3
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "== $Message =="
}

if (-not (Test-Path $StateFile)) {
    throw "State file not found: $StateFile. Run .\create.ps1 first."
}

$state = Get-Content $StateFile | ConvertFrom-Json

Write-Step "Loaded state"
$state | ConvertTo-Json -Depth 5

Write-Step "Delete company"
Invoke-RestMethod -Method DELETE -Uri "$GatewayUrl/company/$($state.companyId)"
Write-Host "DELETE /company/$($state.companyId) -> 204 No Content"

Write-Step "Wait for Kafka flow"
Start-Sleep -Seconds $KafkaWaitSeconds

Write-Step "State after delete"
$usersAfter = Invoke-RestMethod -Uri "$GatewayUrl/user"
$companiesAfter = Invoke-RestMethod -Uri "$GatewayUrl/company"
$employeeAfter = $usersAfter | Where-Object { $_.id -eq $state.employeeId }
$directorAfter = $usersAfter | Where-Object { $_.id -eq $state.directorId }
$companyAfter = $companiesAfter | Where-Object { $_.id -eq $state.companyId }

try {
    Invoke-RestMethod -Uri "$GatewayUrl/company/exists/$($state.companyId)" | Out-Null
    $existsStatus = 204
} catch {
    $existsStatus = [int]$_.Exception.Response.StatusCode
}

@($directorAfter, $employeeAfter) | ConvertTo-Json -Depth 5
$companyAfter | ConvertTo-Json -Depth 5

Write-Step "Summary"
[pscustomobject]@{
    directorId = $state.directorId
    companyId = $state.companyId
    employeeId = $state.employeeId
    employeeCompanyIdAfterDelete = $employeeAfter.companyId
    employeeCompanyNameAfterDelete = $employeeAfter.companyName
    companyVisibleAfterDelete = @($companyAfter).Count -gt 0
    companyExistsStatusAfterDelete = $existsStatus
} | ConvertTo-Json -Depth 5
