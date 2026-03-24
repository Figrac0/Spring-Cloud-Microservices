param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$UserServiceHealthUrl = "http://localhost:8081/actuator/health",
    [string]$CompanyServiceHealthUrl = "http://localhost:8082/actuator/health",
    [int]$StartupTimeoutSeconds = 120,
    [string]$StateFile = ".lab4-demo-state.json"
)

$ErrorActionPreference = "Stop"

$timestamp = Get-Date -Format "yyyyMMddHHmmss"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "== $Message =="
}

function Wait-HttpStatusUp {
    param(
        [string]$Url,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Url
            if ($response.status -eq "UP") {
                Write-Host "Ready: $Url"
                return
            }
        } catch {
        }

        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for UP status from $Url"
}

function Wait-HttpSuccess {
    param(
        [string]$Url,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-RestMethod -Uri $Url | Out-Null
            Write-Host "Ready: $Url"
            return
        } catch {
        }

        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for successful response from $Url"
}

Write-Step "Wait for services and gateway routes"
Wait-HttpStatusUp -Url "$GatewayUrl/actuator/health" -TimeoutSeconds $StartupTimeoutSeconds
Wait-HttpStatusUp -Url $UserServiceHealthUrl -TimeoutSeconds $StartupTimeoutSeconds
Wait-HttpStatusUp -Url $CompanyServiceHealthUrl -TimeoutSeconds $StartupTimeoutSeconds
Wait-HttpSuccess -Url "$GatewayUrl/user/description" -TimeoutSeconds $StartupTimeoutSeconds
Wait-HttpSuccess -Url "$GatewayUrl/company/description" -TimeoutSeconds $StartupTimeoutSeconds

Write-Step "Create director user"
$directorBody = @{
    name = "Lab4 Director $timestamp"
    login = "lab4director$timestamp"
    password = "pass123"
    email = "lab4director$timestamp@example.com"
    companyId = $null
} | ConvertTo-Json

$director = Invoke-RestMethod `
    -Method POST `
    -Uri "$GatewayUrl/user" `
    -ContentType "application/json" `
    -Body $directorBody

$director | ConvertTo-Json -Depth 5

Write-Step "Create company"
$companyBody = @{
    name = "Lab4 Company $timestamp"
    ogrn = $timestamp
    activityDescription = "Kafka deletion flow demo"
    directorId = $director.id
} | ConvertTo-Json

$company = Invoke-RestMethod `
    -Method POST `
    -Uri "$GatewayUrl/company" `
    -ContentType "application/json" `
    -Body $companyBody

$company | ConvertTo-Json -Depth 5

Write-Step "Create employee linked to company"
$employeeBody = @{
    name = "Lab4 Employee $timestamp"
    login = "lab4employee$timestamp"
    password = "pass123"
    email = "lab4employee$timestamp@example.com"
    companyId = $company.id
} | ConvertTo-Json

$employee = Invoke-RestMethod `
    -Method POST `
    -Uri "$GatewayUrl/user" `
    -ContentType "application/json" `
    -Body $employeeBody

$employee | ConvertTo-Json -Depth 5

Write-Step "State after create"
$users = Invoke-RestMethod -Uri "$GatewayUrl/user"
$companies = Invoke-RestMethod -Uri "$GatewayUrl/company"

($users | Where-Object { $_.id -in @($director.id, $employee.id) }) | ConvertTo-Json -Depth 5
($companies | Where-Object { $_.id -eq $company.id }) | ConvertTo-Json -Depth 5

$state = [pscustomobject]@{
    directorId = $director.id
    companyId = $company.id
    employeeId = $employee.id
    timestamp = $timestamp
}

$state | ConvertTo-Json -Depth 5 | Set-Content -Path $StateFile

Write-Step "Saved state"
$state | ConvertTo-Json -Depth 5
Write-Host "State file: $StateFile"
