param(
    [string]$ProjectRoot = ".",
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$ConfigServerUrl = "http://localhost:8888",
    [string]$DiscoveryUrl = "http://localhost:8761",
    [string]$UserServiceUrl = "http://localhost:8081",
    [string]$CompanyServiceUrl = "http://localhost:8082",
    [int]$StartupTimeoutSeconds = 240,
    [int]$AsyncWaitSeconds = 6,
    [string]$ReportPath = ".\demo-report.json",
    [switch]$SkipDockerUp
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host ("=" * 80)
    Write-Host $Message
    Write-Host ("=" * 80)
}

function ConvertFrom-JsonSafe {
    param([string]$Content)

    if ([string]::IsNullOrWhiteSpace($Content)) {
        return $null
    }

    try {
        return $Content | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Get-ResponseBodyFromException {
    param([System.Exception]$Exception)

    try {
        if ($null -eq $Exception.Response) {
            return $Exception.Message
        }

        $stream = $Exception.Response.GetResponseStream()
        if ($null -eq $stream) {
            return $Exception.Message
        }

        $reader = New-Object System.IO.StreamReader($stream)
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Close()
        }
    } catch {
        return $Exception.Message
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = $null,
        [string]$ContentType = $null,
        [string]$Body = $null
    )

    $params = @{
        Method          = $Method
        Uri             = $Uri
        ErrorAction     = "Stop"
        UseBasicParsing = $true
    }

    if ($null -ne $Headers) {
        $params.Headers = $Headers
    }

    if (-not [string]::IsNullOrEmpty($ContentType)) {
        $params.ContentType = $ContentType
    }

    if (-not [string]::IsNullOrEmpty($Body)) {
        $params.Body = $Body
    }

    try {
        $response = Invoke-WebRequest @params
        $rawBody = if ($response.Content -is [byte[]]) {
            [System.Text.Encoding]::UTF8.GetString($response.Content)
        } else {
            [string]$response.Content
        }
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Body       = $rawBody
            Json       = ConvertFrom-JsonSafe $rawBody
        }
    } catch {
        $statusCode = 0
        if ($null -ne $_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }

        $rawBody = Get-ResponseBodyFromException $_.Exception
        return [pscustomobject]@{
            StatusCode = $statusCode
            Body       = $rawBody
            Json       = ConvertFrom-JsonSafe $rawBody
        }
    }
}

function Assert-Status {
    param(
        [string]$Name,
        [int]$Expected,
        $Actual
    )

    if ($Actual -ne $Expected) {
        throw "$Name failed. Expected status $Expected, actual $Actual"
    }
}

function Wait-ForHealth {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Url -ErrorAction Stop
            if ($response.status -eq "UP") {
                Write-Host "Ready: $Name -> $Url"
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for $Name health at $Url"
}

function Wait-ForGatewayAuthRoute {
    param(
        [string]$Url,
        [string]$Login,
        [string]$Password,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $body = @{ login = $Login; password = $Password } | ConvertTo-Json

    while ((Get-Date) -lt $deadline) {
        $response = Invoke-Api -Method "POST" -Uri $Url -ContentType "application/json" -Body $body
        if ($response.StatusCode -eq 200) {
            Write-Host "Ready: gateway auth route -> $Url"
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for gateway auth route at $Url"
}

function Decode-JwtPayload {
    param([string]$Token)

    $payload = $Token.Split(".")[1].Replace("-", "+").Replace("_", "/")
    switch ($payload.Length % 4) {
        2 { $payload += "==" }
        3 { $payload += "=" }
    }

    $json = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload))
    return ConvertFrom-JsonSafe $json
}

function Get-JsonOrThrow {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = $null,
        [string]$Body = $null
    )

    $params = @{
        Method = $Method
        Uri    = $Uri
    }

    if ($null -ne $Headers) {
        $params.Headers = $Headers
    }

    if (-not [string]::IsNullOrEmpty($Body)) {
        $params.ContentType = "application/json"
        $params.Body = $Body
    }

    $response = Invoke-Api @params
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "$Name failed with status $($response.StatusCode). Body: $($response.Body)"
    }
    return $response
}

Set-Location $ProjectRoot

if (-not $SkipDockerUp) {
    Write-Step "docker compose up --build -d"
    docker compose up --build -d
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up --build -d failed"
    }
}

Write-Step "Wait for service health"
Wait-ForHealth -Name "config-server" -Url "$ConfigServerUrl/actuator/health" -TimeoutSeconds $StartupTimeoutSeconds
Wait-ForHealth -Name "discovery-server" -Url "$DiscoveryUrl/actuator/health" -TimeoutSeconds $StartupTimeoutSeconds
Wait-ForHealth -Name "api-gateway" -Url "$GatewayUrl/actuator/health" -TimeoutSeconds $StartupTimeoutSeconds
Wait-ForHealth -Name "user-service" -Url "$UserServiceUrl/actuator/health" -TimeoutSeconds $StartupTimeoutSeconds
Wait-ForHealth -Name "company-service" -Url "$CompanyServiceUrl/actuator/health" -TimeoutSeconds $StartupTimeoutSeconds
Wait-ForGatewayAuthRoute -Url "$GatewayUrl/auth/login" -Login "admin" -Password "admin123" -TimeoutSeconds $StartupTimeoutSeconds

Write-Step "Read security config from Config Server"
$applicationConfig = Get-JsonOrThrow -Name "application config" -Method "GET" -Uri "$ConfigServerUrl/application/default"
$gatewayConfig = Get-JsonOrThrow -Name "gateway config" -Method "GET" -Uri "$ConfigServerUrl/api-gateway/default"
$userConfig = Get-JsonOrThrow -Name "user-service config" -Method "GET" -Uri "$ConfigServerUrl/user-service/default"
$companyConfig = Get-JsonOrThrow -Name "company-service config" -Method "GET" -Uri "$ConfigServerUrl/company-service/default"

Write-Step "Login as admin"
$adminAuth = Get-JsonOrThrow `
    -Name "admin login" `
    -Method "POST" `
    -Uri "$GatewayUrl/auth/login" `
    -Body (@{ login = "admin"; password = "admin123" } | ConvertTo-Json)
$adminToken = $adminAuth.Json.token
$adminPayload = Decode-JwtPayload $adminToken

Write-Step "Register and login regular user"
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$userLogin = "lab5user$stamp"
$userEmail = "lab5user$stamp@example.com"

$registeredUser = Get-JsonOrThrow `
    -Name "register user" `
    -Method "POST" `
    -Uri "$GatewayUrl/auth/register" `
    -Body (@{
        name = "Lab5 User $stamp"
        login = $userLogin
        password = "pass123"
        email = $userEmail
    } | ConvertTo-Json)

$userAuth = Get-JsonOrThrow `
    -Name "user login" `
    -Method "POST" `
    -Uri "$GatewayUrl/auth/login" `
    -Body (@{ login = $userLogin; password = "pass123" } | ConvertTo-Json)
$userToken = $userAuth.Json.token
$userPayload = Decode-JwtPayload $userToken

Write-Step "Negative auth checks in Gateway"
$missingTokenCompany = Invoke-Api -Method "GET" -Uri "$GatewayUrl/company"
$invalidTokenCompany = Invoke-Api -Method "GET" -Uri "$GatewayUrl/company" -Headers @{ Authorization = "Bearer invalid.token.value" }

Write-Step "Direct service protection checks"
$directUserNoAuth = Invoke-Api -Method "GET" -Uri "$UserServiceUrl/users"
$directCompanyNoAuth = Invoke-Api -Method "GET" -Uri "$CompanyServiceUrl/companies"

Write-Step "Positive USER read checks"
$userHeaders = @{ Authorization = "Bearer $userToken" }
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

$userReadUsers = Get-JsonOrThrow -Name "USER read users" -Method "GET" -Uri "$GatewayUrl/user" -Headers $userHeaders
$userReadCompanies = Get-JsonOrThrow -Name "USER read companies" -Method "GET" -Uri "$GatewayUrl/company" -Headers $userHeaders
$userReadUserDescription = Get-JsonOrThrow -Name "USER read user description" -Method "GET" -Uri "$GatewayUrl/user/description" -Headers $userHeaders
$userReadCompanyDescription = Get-JsonOrThrow -Name "USER read company description" -Method "GET" -Uri "$GatewayUrl/company/description" -Headers $userHeaders

Write-Step "Create admin-owned test data through Gateway"
$adminUsersResponse = Get-JsonOrThrow -Name "ADMIN read users" -Method "GET" -Uri "$GatewayUrl/user" -Headers $adminHeaders
$adminUser = @($adminUsersResponse.Json) | Where-Object { $_.login -eq "admin" } | Select-Object -First 1
if ($null -eq $adminUser) {
    throw "admin user not found via GET /user"
}

$ogrn = "9$($stamp.Substring($stamp.Length - 10))"

$companyCreate = Get-JsonOrThrow `
    -Name "ADMIN create company" `
    -Method "POST" `
    -Uri "$GatewayUrl/company" `
    -Headers $adminHeaders `
    -Body (@{
        name = "Lab5 Company $stamp"
        ogrn = $ogrn
        activityDescription = "Lab5 security test company"
        directorId = $adminUser.id
    } | ConvertTo-Json)
$company = $companyCreate.Json

$employeeCreate = Get-JsonOrThrow `
    -Name "ADMIN create employee user" `
    -Method "POST" `
    -Uri "$GatewayUrl/user" `
    -Headers $adminHeaders `
    -Body (@{
        name = "Lab5 Employee $stamp"
        login = "lab5employee$stamp"
        password = "pass123"
        email = "lab5employee$stamp@example.com"
        companyId = $company.id
    } | ConvertTo-Json)
$employee = $employeeCreate.Json

Write-Step "Authorization checks"
$userCreateForbidden = Invoke-Api `
    -Method "POST" `
    -Uri "$GatewayUrl/user" `
    -Headers $userHeaders `
    -ContentType "application/json" `
    -Body (@{
        name = "Forbidden User"
        login = "forbidden$stamp"
        password = "pass123"
        email = "forbidden$stamp@example.com"
    } | ConvertTo-Json)

$userDeleteForbidden = Invoke-Api `
    -Method "DELETE" `
    -Uri "$GatewayUrl/company/$($company.id)" `
    -Headers $userHeaders

Write-Step "ADMIN delete company and wait for Kafka chain"
$adminDeleteCompany = Invoke-Api `
    -Method "DELETE" `
    -Uri "$GatewayUrl/company/$($company.id)" `
    -Headers $adminHeaders
Assert-Status -Name "ADMIN delete company" -Expected 204 -Actual $adminDeleteCompany.StatusCode
Start-Sleep -Seconds $AsyncWaitSeconds

Write-Step "Verify async cleanup after delete"
$companyExistsAfterDelete = Invoke-Api `
    -Method "GET" `
    -Uri "$GatewayUrl/company/exists/$($company.id)" `
    -Headers $adminHeaders

$usersAfterDelete = Get-JsonOrThrow -Name "ADMIN read users after delete" -Method "GET" -Uri "$GatewayUrl/user" -Headers $adminHeaders
$employeeAfterDelete = @($usersAfterDelete.Json) | Where-Object { $_.login -eq $employee.login } | Select-Object -First 1
if ($null -eq $employeeAfterDelete) {
    throw "Employee user not found after delete"
}

$report = [ordered]@{
    startup = [ordered]@{
        configServerHealth  = "UP"
        discoveryHealth     = "UP"
        gatewayHealth       = "UP"
        userServiceHealth   = "UP"
        companyServiceHealth = "UP"
    }
    configServer = [ordered]@{
        jwtSecretPresent = [bool]($applicationConfig.Body -match "jwt.secret")
        jwtExpiration    = $applicationConfig.Json.propertySources[0].source."jwt.expiration"
        whitelist        = @(
            $applicationConfig.Json.propertySources[0].source."security.whitelist[0]"
            $applicationConfig.Json.propertySources[0].source."security.whitelist[1]"
            $applicationConfig.Json.propertySources[0].source."security.whitelist[2]"
        )
        authRoutePresent = [bool]($gatewayConfig.Body -match "Path=/auth/\*\*")
    }
    authentication = [ordered]@{
        adminLoginStatus        = $adminAuth.StatusCode
        adminRoles              = @($adminAuth.Json.roles)
        registerStatus          = $registeredUser.StatusCode
        userLoginStatus         = $userAuth.StatusCode
        userRoles               = @($userAuth.Json.roles)
        userJwtPayloadUsername  = $userPayload.username
        userJwtPayloadRoles     = @($userPayload.roles)
        userJwtPayloadExp       = $userPayload.exp
    }
    gatewayAuthChecks = [ordered]@{
        noTokenStatus      = $missingTokenCompany.StatusCode
        invalidTokenStatus = $invalidTokenCompany.StatusCode
    }
    serviceProtection = [ordered]@{
        directUserNoAuthStatus    = $directUserNoAuth.StatusCode
        directCompanyNoAuthStatus = $directCompanyNoAuth.StatusCode
    }
    authorization = [ordered]@{
        userReadUsersStatus             = $userReadUsers.StatusCode
        userReadCompaniesStatus         = $userReadCompanies.StatusCode
        userReadUserDescriptionStatus   = $userReadUserDescription.StatusCode
        userReadCompanyDescriptionStatus = $userReadCompanyDescription.StatusCode
        userCreateUserForbiddenStatus   = $userCreateForbidden.StatusCode
        userDeleteCompanyForbiddenStatus = $userDeleteForbidden.StatusCode
        adminCreateCompanyStatus        = $companyCreate.StatusCode
        adminCreateUserStatus           = $employeeCreate.StatusCode
        adminDeleteCompanyStatus        = $adminDeleteCompany.StatusCode
    }
    asyncDeletion = [ordered]@{
        deletedCompanyExistsStatus = $companyExistsAfterDelete.StatusCode
        employeeCompanyIdAfterDelete = $employeeAfterDelete.companyId
    }
    createdEntities = [ordered]@{
        regularUserLogin = $userLogin
        companyId        = $company.id
        employeeLogin    = $employee.login
    }
}

$reportJson = $report | ConvertTo-Json -Depth 10
Set-Content -Path $ReportPath -Value $reportJson

Write-Step "Lab 5 report"
Write-Host $reportJson
Write-Host ""
Write-Host "Report file: $ReportPath"
