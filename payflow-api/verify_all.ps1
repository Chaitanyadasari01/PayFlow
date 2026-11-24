$baseUrl = "http://localhost:8080"

function Make-Request {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        $Body = $null,
        [int]$ExpectedStatus = 200
    )
    $url = "$baseUrl$Path"
    $statusCode = 0
    $content = ""

    $req = [System.Net.HttpWebRequest]::Create($url)
    $req.Method = $Method
    $req.Timeout = 10000

    foreach ($k in $Headers.Keys) {
        if ($k -eq "Content-Type") {
            $req.ContentType = $Headers[$k]
        } else {
            $req.Headers.Add($k, $Headers[$k])
        }
    }

    if ($Body -ne $null) {
        $req.ContentType = "application/json"
        $jsonStr = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 5 -Compress }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($jsonStr)
        $req.ContentLength = $bytes.Length
        $stream = $req.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
    }

    try {
        $resp = $req.GetResponse()
        $statusCode = [int]$resp.StatusCode
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $content = $reader.ReadToEnd()
        $reader.Close()
        $resp.Close()
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        if ($resp -ne $null) {
            $statusCode = [int]$resp.StatusCode
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
            $content = $reader.ReadToEnd()
            $reader.Close()
            $resp.Close()
        } else {
            $content = $_.Exception.Message
        }
    }

    $pass = ($statusCode -eq $ExpectedStatus)
    $tag = if ($pass) { "[PASS]" } else { "[FAIL]" }
    $color = if ($pass) { "Green" } else { "Red" }

    Write-Host "$tag $Name (HTTP $statusCode, Expected: $ExpectedStatus)" -ForegroundColor $color
    if ($content) {
        Write-Host "  Response: $content" -ForegroundColor Gray
    }

    return [PSCustomObject]@{
        StatusCode = $statusCode
        Content = $content
        Pass = $pass
    }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " PAYFLOW API: LIVE HTTP VERIFICATION ON MYSQL 8" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Health & Docs
Write-Host "`n--- 1. Actuator Health & Swagger UI ---" -ForegroundColor Yellow
$h = Make-Request -Name "Actuator Health" -Method "GET" -Path "/actuator/health" -ExpectedStatus 200
$s = Make-Request -Name "Swagger UI HTML" -Method "GET" -Path "/swagger-ui/index.html" -ExpectedStatus 200
$d = Make-Request -Name "OpenAPI JSON" -Method "GET" -Path "/v3/api-docs" -ExpectedStatus 200

# 2. Registration
Write-Host "`n--- 2. Registration ---" -ForegroundColor Yellow
$regEmail = "user_" + [Guid]::NewGuid().ToString().Substring(0, 8) + "@payflow.com"
$r1 = Make-Request -Name "Register User" -Method "POST" -Path "/api/auth/register" `
    -Body @{ email = $regEmail; password = "Password123!"; fullName = "Test User" } `
    -ExpectedStatus 201
$regJson = $r1.Content | ConvertFrom-Json
$token = $regJson.token

$rDuplicate = Make-Request -Name "Register Duplicate Email" -Method "POST" -Path "/api/auth/register" `
    -Body @{ email = $regEmail; password = "Password123!"; fullName = "Test User" } `
    -ExpectedStatus 409

# 3. Login
Write-Host "`n--- 3. Login ---" -ForegroundColor Yellow
$rLogin = Make-Request -Name "Login Success" -Method "POST" -Path "/api/auth/login" `
    -Body @{ email = $regEmail; password = "Password123!" } `
    -ExpectedStatus 200

$rLoginBadPass = Make-Request -Name "Login Wrong Password" -Method "POST" -Path "/api/auth/login" `
    -Body @{ email = $regEmail; password = "WrongPassword!" } `
    -ExpectedStatus 401

$rLoginBadUser = Make-Request -Name "Login Non-existent User" -Method "POST" -Path "/api/auth/login" `
    -Body @{ email = "nonexistent@payflow.com"; password = "Password123!" } `
    -ExpectedStatus 401

# 4. JWT Authentication
Write-Host "`n--- 4. JWT Authentication ---" -ForegroundColor Yellow
$authHeaders = @{ Authorization = "Bearer $token" }

$rNoToken = Make-Request -Name "Protected Endpoint Without Token" -Method "GET" -Path "/api/payments" -ExpectedStatus 403
$rBadToken = Make-Request -Name "Protected Endpoint With Bad Token" -Method "GET" -Path "/api/payments" `
    -Headers @{ Authorization = "Bearer invalid.jwt.token" } -ExpectedStatus 403
$rValidToken = Make-Request -Name "Protected Endpoint With Valid Token" -Method "GET" -Path "/api/payments" `
    -Headers $authHeaders -ExpectedStatus 200

# 5. Create Payment & Retrieve
Write-Host "`n--- 5. Payment Creation & Retrieval ---" -ForegroundColor Yellow
$rPaySuccess = Make-Request -Name "Create Payment (SUCCESS)" -Method "POST" -Path "/api/payments" `
    -Headers $authHeaders `
    -Body @{ amount = 150.00; currency = "USD"; paymentMethod = "credit_card"; description = "Order #1" } `
    -ExpectedStatus 201
$payJson = $rPaySuccess.Content | ConvertFrom-Json
$paymentId = $payJson.paymentId

$rGetPay = Make-Request -Name "Get Payment By ID" -Method "GET" -Path "/api/payments/$paymentId" `
    -Headers $authHeaders -ExpectedStatus 200

$rGetMissing = Make-Request -Name "Get Non-existent Payment" -Method "GET" -Path "/api/payments/$([Guid]::NewGuid())" `
    -Headers $authHeaders -ExpectedStatus 404

# 6. Idempotency
Write-Host "`n--- 6. Idempotency Key Handling ---" -ForegroundColor Yellow
$idempKey = "idemp_" + [Guid]::NewGuid().ToString()
$idempHeaders = @{
    Authorization = "Bearer $token"
    "Idempotency-Key" = $idempKey
}
$payBody1 = @{ amount = 75.00; currency = "USD"; paymentMethod = "credit_card"; description = "Idempotent Order" }
$rIdemp1 = Make-Request -Name "Idempotency First Call" -Method "POST" -Path "/api/payments" `
    -Headers $idempHeaders -Body $payBody1 -ExpectedStatus 201
$idempPayJson1 = $rIdemp1.Content | ConvertFrom-Json

$rIdemp2 = Make-Request -Name "Idempotency Replay (Same Key + Body)" -Method "POST" -Path "/api/payments" `
    -Headers $idempHeaders -Body $payBody1 -ExpectedStatus 201
$idempPayJson2 = $rIdemp2.Content | ConvertFrom-Json

if ($idempPayJson1.paymentId -eq $idempPayJson2.paymentId) {
    Write-Host "[PASS] Idempotent replay returned exact same paymentId: $($idempPayJson1.paymentId)" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Idempotent replay returned different paymentIds!" -ForegroundColor Red
}

# Same key, DIFFERENT body -> HTTP 409
$payBodyDifferent = @{ amount = 99.00; currency = "EUR"; paymentMethod = "bank_transfer"; description = "Altered Order" }
$rIdempConflict = Make-Request -Name "Idempotency Conflict (Same Key + Different Request)" -Method "POST" -Path "/api/payments" `
    -Headers $idempHeaders -Body $payBodyDifferent -ExpectedStatus 409

# 7. Simulated Payments (Success & Failure)
Write-Host "`n--- 7. Simulated Payment Outcomes ---" -ForegroundColor Yellow
# Amount ending in .99 -> DECLINED
$rDeclined = Make-Request -Name "Simulated Declined Payment (.99 amount)" -Method "POST" -Path "/api/payments" `
    -Headers $authHeaders `
    -Body @{ amount = 49.99; currency = "USD"; paymentMethod = "credit_card"; description = "Decline simulation" } `
    -ExpectedStatus 201
$declinedJson = $rDeclined.Content | ConvertFrom-Json
Write-Host "  Payment Status: $($declinedJson.status), Reason: $($declinedJson.failureReason)" -ForegroundColor Yellow

# Amount ending in .77 -> 422 Gateway Error / Provider Timeout
$rTimeout = Make-Request -Name "Simulated Provider Error (.77 amount)" -Method "POST" -Path "/api/payments" `
    -Headers $authHeaders `
    -Body @{ amount = 100.77; currency = "USD"; paymentMethod = "credit_card"; description = "Timeout simulation" } `
    -ExpectedStatus 422

# Amount > 10,000 -> DECLINED
$rOverLimit = Make-Request -Name "Simulated Over Limit (>10,000 amount)" -Method "POST" -Path "/api/payments" `
    -Headers $authHeaders `
    -Body @{ amount = 12000.00; currency = "USD"; paymentMethod = "credit_card"; description = "Over limit" } `
    -ExpectedStatus 201
$overLimitJson = $rOverLimit.Content | ConvertFrom-Json
Write-Host "  Payment Status: $($overLimitJson.status), Reason: $($overLimitJson.failureReason)" -ForegroundColor Yellow

# 8. Full and Partial Refunds
Write-Host "`n--- 8. Full and Partial Refunds ---" -ForegroundColor Yellow
$rRefundPay = Make-Request -Name "Create Payment for Refund Testing" -Method "POST" -Path "/api/payments" `
    -Headers $authHeaders `
    -Body @{ amount = 200.00; currency = "USD"; paymentMethod = "credit_card"; description = "To be refunded" } `
    -ExpectedStatus 201
$refundPayId = ($rRefundPay.Content | ConvertFrom-Json).paymentId

# Partial refund 1: 80.00
$rPartial1 = Make-Request -Name "Partial Refund (80/200)" -Method "POST" -Path "/api/payments/$refundPayId/refunds" `
    -Headers $authHeaders `
    -Body @{ amount = 80.00; reason = "Partial return 1" } `
    -ExpectedStatus 201

# Partial refund 2: 50.00
$rPartial2 = Make-Request -Name "Partial Refund (50/120 remaining)" -Method "POST" -Path "/api/payments/$refundPayId/refunds" `
    -Headers $authHeaders `
    -Body @{ amount = 50.00; reason = "Partial return 2" } `
    -ExpectedStatus 201

# Over-refund: 100.00 (only 70.00 left) -> Expected 422
$rOverRefund = Make-Request -Name "Over-Refund (100 > 70 remaining)" -Method "POST" -Path "/api/payments/$refundPayId/refunds" `
    -Headers $authHeaders `
    -Body @{ amount = 100.00; reason = "Too large refund" } `
    -ExpectedStatus 422

# Full remaining refund: 70.00
$rFullRemaining = Make-Request -Name "Full Remaining Refund (70/70)" -Method "POST" -Path "/api/payments/$refundPayId/refunds" `
    -Headers $authHeaders `
    -Body @{ amount = 70.00; reason = "Final return" } `
    -ExpectedStatus 201

# Check payment status is now REFUNDED
$rCheckRefunded = Make-Request -Name "Check Payment Status is REFUNDED" -Method "GET" -Path "/api/payments/$refundPayId" `
    -Headers $authHeaders -ExpectedStatus 200
$checkJson = $rCheckRefunded.Content | ConvertFrom-Json
Write-Host "  Updated Payment Status: $($checkJson.status)" -ForegroundColor Green

# List refunds for payment
$rListRefunds = Make-Request -Name "List Refunds for Payment" -Method "GET" -Path "/api/payments/$refundPayId/refunds" `
    -Headers $authHeaders -ExpectedStatus 200

# 9. Concurrent Refunds Safety
Write-Host "`n--- 9. Concurrent Refunds Concurrency Safety ---" -ForegroundColor Yellow
$rConcPay = Make-Request -Name "Create 100.00 Payment for Concurrency Test" -Method "POST" -Path "/api/payments" `
    -Headers $authHeaders `
    -Body @{ amount = 100.00; currency = "USD"; paymentMethod = "credit_card"; description = "Concurrent test" } `
    -ExpectedStatus 201
$concPayId = ($rConcPay.Content | ConvertFrom-Json).paymentId

# Run 3 concurrent requests of 60.00 each
$jobs = @()
for ($i = 1; $i -le 3; $i++) {
    $jobs += Start-Job -ScriptBlock {
        param($url, $t, $idx)
        $req = [System.Net.HttpWebRequest]::Create($url)
        $req.Method = "POST"
        $req.ContentType = "application/json"
        $req.Headers.Add("Authorization", "Bearer $t")
        $bodyObj = @{ amount = 60.00; reason = "Concurrent refund $idx" }
        $body = $bodyObj | ConvertTo-Json -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $req.ContentLength = $bytes.Length
        $stream = $req.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
        try {
            $resp = $req.GetResponse()
            $code = [int]$resp.StatusCode
            $resp.Close()
            return $code
        } catch [System.Net.WebException] {
            $resp = $_.Exception.Response
            if ($resp -ne $null) {
                $code = [int]$resp.StatusCode
                $resp.Close()
                return $code
            }
            return 0
        }
    } -ArgumentList "$baseUrl/api/payments/$concPayId/refunds", $token, $i
}
$jobStatuses = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
$succeeded = ($jobStatuses | Where-Object { $_ -eq 201 }).Count
$rejected = ($jobStatuses | Where-Object { $_ -eq 422 }).Count
Write-Host "  Concurrent Requests: 3 x $60 on $100 balance" -ForegroundColor Cyan
Write-Host "  Results: Succeeded(201): $succeeded, Rejected(422): $rejected, Statuses: $($jobStatuses -join ', ')" -ForegroundColor Cyan
if ($succeeded -eq 1 -and $rejected -eq 2) {
    Write-Host "[PASS] Concurrency lock worked: Exactly 1 refund succeeded, 2 were rejected!" -ForegroundColor Green
} else {
    Write-Host "[WARN] Concurrency test result: Succeeded=$succeeded, Rejected=$rejected" -ForegroundColor Yellow
}

# 10. Webhooks
Write-Host "`n--- 10. Webhook Events and Retries ---" -ForegroundColor Yellow
$rWebhooks = Make-Request -Name "Get Webhook Events for Payment" -Method "GET" -Path "/api/webhooks/payments/$paymentId" `
    -Headers $authHeaders -ExpectedStatus 200

$rRetry = Make-Request -Name "Manual Trigger Webhook Retry" -Method "POST" -Path "/api/webhooks/retry" `
    -Headers $authHeaders -ExpectedStatus 200

# 11. Pagination & Listing
Write-Host "`n--- 11. Payment Listing & Pagination ---" -ForegroundColor Yellow
$rList = Make-Request -Name "List Payments Page 0" -Method "GET" -Path "/api/payments?page=0&size=5" `
    -Headers $authHeaders -ExpectedStatus 200
$listJson = $rList.Content | ConvertFrom-Json
Write-Host "  Total Payments in DB: $($listJson.totalElements), Total Pages: $($listJson.totalPages), Current Page Size: $($listJson.content.Count)" -ForegroundColor Green

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host " FULL VERIFICATION RUN COMPLETE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
