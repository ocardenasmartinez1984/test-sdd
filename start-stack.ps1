<#
.SYNOPSIS
    Arranque en fases del stack POS para evitar picos de CPU/memoria.

.DESCRIPTION
    Levanta el stack esencial por capas, esperando que cada fase esté "healthy"
    antes de continuar. Esto evita saturar la máquina al arrancar 14+ contenedores
    (6 JVMs + Kafka + bases de datos) simultáneamente.

    Las herramientas pesadas (Jenkins, SonarQube, Prometheus, Grafana) están detrás
    de perfiles de Docker Compose y NO arrancan por defecto. Usa -Tooling para incluirlas.

.PARAMETER Build
    Reconstruye las imágenes antes de arrancar. BuildKit aprovecha la caché de Gradle/npm
    compartida, así que las reconstrucciones son mucho más rápidas que la primera vez.

.PARAMETER Tooling
    Incluye también las herramientas de CI/CD y monitoreo (perfil "tooling").

.PARAMETER Down
    Detiene y elimina todos los contenedores del stack (incluye perfiles).

.EXAMPLE
    ./start-stack.ps1
    Arranca el stack esencial usando las imágenes ya construidas.

.EXAMPLE
    ./start-stack.ps1 -Build
    Reconstruye (con caché) y arranca el stack esencial.

.EXAMPLE
    ./start-stack.ps1 -Tooling
    Arranca el stack esencial + Jenkins/SonarQube/Prometheus/Grafana.

.EXAMPLE
    ./start-stack.ps1 -Down
    Baja todo el stack.
#>
param(
    [switch]$Build,
    [switch]$Tooling,
    [switch]$Down
)

$ErrorActionPreference = "Stop"

# Habilita BuildKit para usar los cache mounts de los Dockerfiles.
$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_DOCKER_CLI_BUILD = "1"

# Detecta 'docker compose' (v2) vs 'docker-compose' (v1)
$script:UseComposeV2 = $true
try {
    docker compose version *> $null
    if ($LASTEXITCODE -ne 0) { $script:UseComposeV2 = $false }
} catch {
    $script:UseComposeV2 = $false
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$ComposeArgs)
    if ($script:UseComposeV2) {
        & docker compose @ComposeArgs
    } else {
        & docker-compose @ComposeArgs
    }
    if ($LASTEXITCODE -ne 0) { throw "docker compose falló: $($ComposeArgs -join ' ')" }
}

function Wait-Healthy {
    param(
        [string[]]$Services,
        [int]$TimeoutSeconds = 180
    )
    Write-Host "  Esperando a que estén healthy: $($Services -join ', ')" -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $allReady = $true
        foreach ($svc in $Services) {
            $name = "saga-$svc"
            $status = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $name 2>$null
            if ($status -ne "healthy" -and $status -ne "running") {
                $allReady = $false
                break
            }
        }
        if ($allReady) {
            Write-Host "  OK: fase healthy." -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 3
    }
    Write-Warning "  Timeout esperando salud de: $($Services -join ', '). Continuando de todos modos."
}

if ($Down) {
    Write-Host "Bajando todo el stack (incluye perfiles)..." -ForegroundColor Yellow
    $downArgs = @("--profile", "tooling", "down")
    Invoke-Compose @downArgs
    Write-Host "Stack detenido." -ForegroundColor Green
    return
}

# Argumentos de build opcionales
$upArgs = @("up", "-d")
if ($Build) { $upArgs += "--build" }

Write-Host "=== Arranque en fases del stack POS ===" -ForegroundColor Magenta
if ($Build) { Write-Host "(reconstruyendo imágenes con caché BuildKit)" -ForegroundColor DarkGray }

# --- Fase 1: Infraestructura base (datos y mensajería) ---
Write-Host "`n[Fase 1/3] Infraestructura: postgres, mongodb, kafka, redis" -ForegroundColor Yellow
$phase1 = $upArgs + @("postgres", "mongodb", "kafka", "redis")
Invoke-Compose @phase1
Wait-Healthy -Services @("postgres", "mongodb", "kafka", "redis")

# --- Fase 2: Service discovery + microservicios backend ---
Write-Host "`n[Fase 2/3] Backend: eureka, api-gateway, auth, stock, venta, despacho" -ForegroundColor Yellow
# Eureka primero para que los demás se registren.
$phase2a = $upArgs + @("eureka-server")
Invoke-Compose @phase2a
Wait-Healthy -Services @("eureka-server")
$phase2b = $upArgs + @("api-gateway", "auth-service", "stock-service", "venta-service", "despacho-service")
Invoke-Compose @phase2b
Wait-Healthy -Services @("api-gateway", "auth-service", "stock-service", "venta-service", "despacho-service") -TimeoutSeconds 240

# --- Fase 3: Frontends ---
Write-Host "`n[Fase 3/3] Frontends: pos-frontend, ventas-mantenedor, users-mantenedor" -ForegroundColor Yellow
$phase3 = $upArgs + @("pos-frontend", "ventas-mantenedor", "users-mantenedor")
Invoke-Compose @phase3
Wait-Healthy -Services @("pos-frontend", "ventas-mantenedor", "users-mantenedor")

# --- Tooling opcional ---
if ($Tooling) {
    Write-Host "`n[Extra] Tooling: jenkins, sonarqube, prometheus, grafana, zipkin" -ForegroundColor Yellow
    $toolingArgs = @("--profile", "tooling", "up", "-d")
    Invoke-Compose @toolingArgs
}

Write-Host "`n=== Stack levantado ===" -ForegroundColor Green
Write-Host @"

  POS Frontend        http://localhost:4300
  Ventas Mantenedor   http://localhost:4200
  Users Mantenedor    http://localhost:4400
  API Gateway         http://localhost:8080
  Eureka Dashboard    http://localhost:8761
"@ -ForegroundColor Cyan

if ($Tooling) {
    Write-Host @"
  Jenkins             http://localhost:8888
  SonarQube           http://localhost:9000
  Prometheus          http://localhost:9090
  Grafana             http://localhost:3001
  Zipkin              http://localhost:9411
"@ -ForegroundColor DarkCyan
}

Write-Host "`nVer estado:  docker compose ps" -ForegroundColor DarkGray
Write-Host "Bajar todo:  ./start-stack.ps1 -Down" -ForegroundColor DarkGray
