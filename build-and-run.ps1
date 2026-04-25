param(
    [Alias('s')][switch]$SkipTests,
    [Alias('d')][switch]$Detach,
    [Alias('h')][switch]$Help
)

if ($Help) {
    Write-Host "Usage: .\build-and-run.ps1 [-SkipTests|-s] [-Detach|-d]"
    Write-Host "  -s  Skip unit tests during Maven build (faster)"
    Write-Host "  -d  Start Docker Compose in detached mode"
    exit 0
}

function Write-Ok   { param($msg) Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Info { param($msg) Write-Host "[>>] $msg" -ForegroundColor Yellow }
function Write-Fail { param($msg) Write-Host "[FAIL] $msg" -ForegroundColor Red; exit 1 }

$RootDir = $PSScriptRoot

$MvnArgs = "clean", "package", "--no-transfer-progress"
if ($SkipTests) { $MvnArgs += "-DskipTests" }

$ComposeArgs = "up", "--build"
if ($Detach) { $ComposeArgs += "-d" }

$Services = "wallet_system", "payment-service", "email-service", "mock-bank"

foreach ($svc in $Services) {
    $svcDir = Join-Path $RootDir $svc
    if (-not (Test-Path $svcDir)) {
        Write-Info "Skipping $svc (directory not found)"
        continue
    }
    Write-Info "Building $svc ..."
    Push-Location $svcDir
    $mvnCmd = if (Test-Path "./mvnw") { "./mvnw" } else { "mvn" }
    & $mvnCmd @MvnArgs
    $exitCode = $LASTEXITCODE
    Pop-Location
    if ($exitCode -ne 0) { Write-Fail "Build failed: $svc" }
    Write-Ok "$svc built"
}

Write-Host ""
Write-Ok "All services built. Starting Docker Compose ..."
Write-Host ""

Set-Location $RootDir
& docker compose @ComposeArgs
