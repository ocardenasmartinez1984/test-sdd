## Prueba de Estres - POS Microservices
## Ejecuta requests concurrentes contra los endpoints principales

param(
    [int]$ConcurrentUsers = 20,
    [int]$RequestsPerUser = 10,
    [int]$ThinkTimeMs = 100
)

$ErrorActionPreference = "SilentlyContinue"
$baseUrl = "http://localhost:8080"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  PRUEBA DE ESTRES - POS Microservices" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Usuarios concurrentes: $ConcurrentUsers"
Write-Host "  Requests por usuario:  $RequestsPerUser"
Write-Host "  Total requests:        $($ConcurrentUsers * $RequestsPerUser * 4)"
Write-Host "  Think time:            ${ThinkTimeMs}ms"
Write-Host ""

# Obtener token
Write-Host ">>> Obteniendo token de autenticacion..." -ForegroundColor Yellow
$loginBody = '{"username":"admin","password":"admin123"}'
$loginResp = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody -UseBasicParsing
$token = ($loginResp.Content | ConvertFrom-Json).token
if (-not $token) { Write-Host "ERROR: No se pudo obtener token" -ForegroundColor Red; exit 1 }
Write-Host "  Token obtenido OK" -ForegroundColor Green

# Obtener productos
$products = (Invoke-WebRequest -Uri "$baseUrl/api/stock" -UseBasicParsing).Content | ConvertFrom-Json
$productIds = $products | ForEach-Object { $_.id }
Write-Host "  Productos disponibles: $($productIds.Count)" -ForegroundColor Green
Write-Host ""

# Contadores thread-safe
$results = [System.Collections.Concurrent.ConcurrentBag[object]]::new()

# Endpoints a probar
$endpoints = @(
    @{ Name = "GET /api/stock"; Method = "GET"; Url = "$baseUrl/api/stock" },
    @{ Name = "GET /api/ventas"; Method = "GET"; Url = "$baseUrl/api/ventas" },
    @{ Name = "GET /api/users"; Method = "GET"; Url = "$baseUrl/api/users" },
    @{ Name = "POST /api/auth/login"; Method = "POST"; Url = "$baseUrl/api/auth/login"; Body = $loginBody }
)

Write-Host ">>> Iniciando prueba de estres..." -ForegroundColor Yellow
Write-Host ""

$startTime = Get-Date

# Ejecutar requests concurrentes
$jobs = @()
for ($u = 1; $u -le $ConcurrentUsers; $u++) {
    $jobs += Start-Job -ScriptBlock {
        param($endpoints, $RequestsPerUser, $ThinkTimeMs, $token, $baseUrl, $productIds)
        
        $localResults = @()
        
        for ($r = 1; $r -le $RequestsPerUser; $r++) {
            foreach ($ep in $endpoints) {
                $sw = [System.Diagnostics.Stopwatch]::StartNew()
                try {
                    $headers = @{ "Authorization" = "Bearer $token" }
                    if ($ep.Method -eq "GET") {
                        $resp = Invoke-WebRequest -Uri $ep.Url -Headers $headers -UseBasicParsing -TimeoutSec 30
                    } else {
                        $resp = Invoke-WebRequest -Uri $ep.Url -Method POST -ContentType "application/json" -Body $ep.Body -Headers $headers -UseBasicParsing -TimeoutSec 30
                    }
                    $sw.Stop()
                    $localResults += @{ Name = $ep.Name; Status = $resp.StatusCode; Time = $sw.ElapsedMilliseconds; Error = $false }
                } catch {
                    $sw.Stop()
                    $status = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.Value__ } else { 0 }
                    $localResults += @{ Name = $ep.Name; Status = $status; Time = $sw.ElapsedMilliseconds; Error = $true }
                }
            }
            Start-Sleep -Milliseconds $ThinkTimeMs
        }
        
        return $localResults
    } -ArgumentList $endpoints, $RequestsPerUser, $ThinkTimeMs, $token, $baseUrl, $productIds
}

# Esperar a que terminen
Write-Host "  Esperando finalizacion de $($jobs.Count) workers..." -ForegroundColor Gray
$allResults = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

$endTime = Get-Date
$totalTime = ($endTime - $startTime).TotalSeconds

# Procesar resultados
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  RESULTADOS" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Duracion total: $([math]::Round($totalTime, 2)) segundos"
Write-Host "  Total requests: $($allResults.Count)"
Write-Host "  Throughput:     $([math]::Round($allResults.Count / $totalTime, 1)) req/s"
Write-Host ""

# Agrupar por endpoint
$grouped = $allResults | Group-Object { $_.Name }

Write-Host ("{0,-25} {1,8} {2,8} {3,10} {4,10} {5,10} {6,8}" -f "ENDPOINT", "TOTAL", "OK", "AVG(ms)", "P95(ms)", "MAX(ms)", "ERRORS")
Write-Host ("{0,-25} {1,8} {2,8} {3,10} {4,10} {5,10} {6,8}" -f "--------", "-----", "--", "-------", "-------", "-------", "------")

foreach ($group in $grouped) {
    $items = $group.Group
    $ok = ($items | Where-Object { -not $_.Error }).Count
    $errors = ($items | Where-Object { $_.Error }).Count
    $times = $items | ForEach-Object { $_.Time } | Sort-Object
    $avg = [math]::Round(($times | Measure-Object -Average).Average, 0)
    $p95Index = [math]::Floor($times.Count * 0.95)
    $p95 = $times[$p95Index]
    $max = ($times | Measure-Object -Maximum).Maximum
    
    $color = if ($errors -gt 0) { "Red" } else { "Green" }
    Write-Host ("{0,-25} {1,8} {2,8} {3,10} {4,10} {5,10} {6,8}" -f $group.Name, $items.Count, $ok, $avg, $p95, $max, $errors) -ForegroundColor $color
}

# Resumen
$totalErrors = ($allResults | Where-Object { $_.Error }).Count
$errorRate = [math]::Round(($totalErrors / $allResults.Count) * 100, 2)
$allTimes = $allResults | ForEach-Object { $_.Time } | Sort-Object
$globalAvg = [math]::Round(($allTimes | Measure-Object -Average).Average, 0)
$globalP95 = $allTimes[[math]::Floor($allTimes.Count * 0.95)]

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  RESUMEN" -ForegroundColor Cyan  
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Latencia promedio:  ${globalAvg}ms"
Write-Host "  Latencia P95:       ${globalP95}ms"
Write-Host "  Error rate:         ${errorRate}%"
Write-Host "  Requests exitosos:  $($allResults.Count - $totalErrors) / $($allResults.Count)"
Write-Host ""

if ($errorRate -lt 1) {
    Write-Host "  RESULTADO: PASS" -ForegroundColor Green
} elseif ($errorRate -lt 5) {
    Write-Host "  RESULTADO: WARNING - Error rate elevado" -ForegroundColor Yellow
} else {
    Write-Host "  RESULTADO: FAIL - Demasiados errores" -ForegroundColor Red
}
Write-Host ""
