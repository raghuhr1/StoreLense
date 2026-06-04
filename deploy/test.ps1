
#!/usr/bin/env pwsh
# StoreLense full-stack integration test
# Run after: docker compose up -d

param(
    [string]$Gateway = "http://localhost:8080",
    [string]$AuthUrl  = "http://localhost:8081",
    [string]$Username = "admin",
    [string]$Password = "Admin@StoreLense1"
)

$ErrorActionPreference = "Stop"
$pass = 0; $fail = 0

function Test-Endpoint {
    param([string]$Name, [string]$Url, [string]$Method = "GET",
          [hashtable]$Headers = @{}, [string]$Body = "", [int]$Expected = 200)

    try {
        $splat = @{ Uri = $Url; Method = $Method; UseBasicParsing = $true; TimeoutSec = 15 }
        if ($Headers.Count) { $splat.Headers = $Headers }
        if ($Body)  { $splat.Body = $Body; $splat.ContentType = "application/json" }

        $resp = Invoke-WebRequest @splat -ErrorAction SilentlyContinue
        if ($resp.StatusCode -eq $Expected) {
            Write-Host "  [PASS] $Name ($($resp.StatusCode))" -ForegroundColor Green
            $script:pass++
            return $resp
        } else {
            Write-Host "  [FAIL] $Name — got $($resp.StatusCode), expected $Expected" -ForegroundColor Red
            $script:fail++
            return $null
        }
    } catch {
        Write-Host "  [FAIL] $Name — $($_.Exception.Message)" -ForegroundColor Red
        $script:fail++
        return $null
    }
}

Write-Host "`n═══ StoreLense Integration Tests ═══" -ForegroundColor Cyan
Write-Host "Gateway : $Gateway"
Write-Host "Auth    : $AuthUrl`n"

# ── 1. Infrastructure health ──────────────────────────────────────────────────
Write-Host "▶ Infrastructure" -ForegroundColor Yellow
Test-Endpoint "Gateway /health"        "$Gateway/health"
Test-Endpoint "Auth actuator/health"   "$AuthUrl/actuator/health"

# ── 2. Login + get JWT ────────────────────────────────────────────────────────
Write-Host "`n▶ Authentication" -ForegroundColor Yellow
$loginBody = '{"username":"' + $Username + '","password":"' + $Password + '"}'
$loginResp = Test-Endpoint "POST /api/auth/login" "$Gateway/api/auth/login" "POST" @{} $loginBody 200

$token = $null
if ($loginResp) {
    $data = ($loginResp.Content | ConvertFrom-Json)
    $token = $data.data.accessToken
    Write-Host "    JWT: $($token.Substring(0,[Math]::Min(40,$token.Length)))…"
}

$authHeader = @{ Authorization = "Bearer $token" }

# ── 3. Authenticated endpoints ────────────────────────────────────────────────
if ($token) {
    Write-Host "`n▶ Stores API" -ForegroundColor Yellow
    Test-Endpoint "GET /api/stores"           "$Gateway/api/stores"          "GET" $authHeader

    Write-Host "`n▶ Products API" -ForegroundColor Yellow
    Test-Endpoint "GET /api/products"         "$Gateway/api/products"        "GET" $authHeader

    Write-Host "`n▶ SOH Sessions API" -ForegroundColor Yellow
    $storeResp = Invoke-WebRequest -Uri "$Gateway/api/stores" -Headers $authHeader -UseBasicParsing -ErrorAction SilentlyContinue
    $storeId = $null
    if ($storeResp) {
        $stores = ($storeResp.Content | ConvertFrom-Json).data.content
        if ($stores.Count -gt 0) { $storeId = $stores[0].id }
    }

    if ($storeId) {
        Test-Endpoint "GET /api/soh/sessions"  "$Gateway/api/soh/sessions?storeId=$storeId" "GET" $authHeader
        Test-Endpoint "GET /api/refill/tasks"  "$Gateway/api/refill/tasks?storeId=$storeId" "GET" $authHeader
        Test-Endpoint "GET /api/inventory/state" "$Gateway/api/inventory/state?storeId=$storeId" "GET" $authHeader
    } else {
        Write-Host "  [SKIP] SOH/Refill/Inventory — no stores found" -ForegroundColor DarkGray
    }

    Write-Host "`n▶ RFID Ingest API" -ForegroundColor Yellow
    if ($storeId) {
        $batchBody = @{
            rfidSessionId = [Guid]::NewGuid().ToString()
            storeId       = $storeId
            deviceId      = "test-device-001"
            readerId      = $null
            reads         = @(
                @{ epc = "3034257BF400B71400000001"; rssi = -68.5; antennaPort = 0; readAt = (Get-Date -Format "o") }
                @{ epc = "3034257BF400B71400000002"; rssi = -72.1; antennaPort = 0; readAt = (Get-Date -Format "o") }
                @{ epc = "3034257BF400B71400000003"; rssi = -65.0; antennaPort = 1; readAt = (Get-Date -Format "o") }
            )
        } | ConvertTo-Json -Depth 5

        Test-Endpoint "POST /api/rfid/ingest/batch" "$Gateway/api/rfid/ingest/batch" "POST" $authHeader $batchBody 202
    }

    Write-Host "`n▶ Reporting API" -ForegroundColor Yellow
    if ($storeId) {
        $from = (Get-Date).AddDays(-7).ToString("yyyy-MM-dd")
        $to   = (Get-Date).ToString("yyyy-MM-dd")
        Test-Endpoint "GET /api/reporting/kpi/range" "$Gateway/api/reporting/kpi/range?storeId=$storeId&from=$from&to=$to" "GET" $authHeader
    }

    Write-Host "`n▶ Token Refresh" -ForegroundColor Yellow
    $refreshBody = '{"refreshToken":"' + ($loginResp.Content | ConvertFrom-Json).data.refreshToken + '"}'
    Test-Endpoint "POST /api/auth/refresh" "$Gateway/api/auth/refresh" "POST" @{} $refreshBody 200
}

# ── Results summary ────────────────────────────────────────────────────────────
$total = $pass + $fail
Write-Host "`n═══ Results: $pass/$total passed ═══" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Yellow" })
if ($fail -gt 0) {
    Write-Host "$fail test(s) FAILED" -ForegroundColor Red
    exit 1
} else {
    Write-Host "All tests PASSED" -ForegroundColor Green
    exit 0
}
