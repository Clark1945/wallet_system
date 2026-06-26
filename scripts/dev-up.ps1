<#
.SYNOPSIS
    Build & run the local stack with git-derived, per-service versions (PowerShell port of dev-up.sh).

.DESCRIPTION
    Lightweight automatic versioning for local demo / portfolio use (no registry).
    Each service's version is computed on the host from git -- the short SHA of the
    last commit that touched that service's directory -- and passed into the build as
    APP_VERSION (-> -Drevision -> pom version + build-info -> /actuator/info) and as
    its docker image tag. The Docker build container has no .git, which is exactly why
    the version is resolved here and injected as a build arg.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1
    powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1 -d
    powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1 -d email-service

    Any extra args are passed straight through to `docker compose up --build`.

.NOTES
    Plain `docker compose up --build` still works without this script -- it falls back
    to the VERSION defaults in .env (currently 1.0.0).
#>

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")   # repo root

# Human-controlled semantic base; bump for a "real" release. The git SHA after it
# is fully automatic, so day-to-day you never edit a version by hand.
$VersionBase = "1.0.0"

# Returns "<base>-<short-sha>[-dirty]" for the given service directory.
function Get-ServiceVersion {
    param([string]$Dir)

    $sha = (git log -1 --format=%h -- $Dir 2>$null | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($sha)) { $sha = "nogit" }
    $sha = $sha.Trim()

    # Uncommitted changes in this service dir (working tree or index) -> mark dirty.
    git diff --quiet -- $Dir 2>$null
    $dirtyWorkTree = ($LASTEXITCODE -ne 0)
    git diff --cached --quiet -- $Dir 2>$null
    $dirtyIndex = ($LASTEXITCODE -ne 0)
    $dirty = if ($dirtyWorkTree -or $dirtyIndex) { "-dirty" } else { "" }

    return "$VersionBase-$sha$dirty"
}

$env:WALLET_VERSION   = Get-ServiceVersion "wallet_system"
$env:PAYMENT_VERSION  = Get-ServiceVersion "payment-service"
$env:EMAIL_VERSION    = Get-ServiceVersion "email-service"
$env:AUDIT_VERSION    = Get-ServiceVersion "audit-service"
$env:MOCKBANK_VERSION = Get-ServiceVersion "mock-bank"

Write-Host "Building with git-derived versions:"
Write-Host ("  {0,-16} {1}" -f "wallet_system",   $env:WALLET_VERSION)
Write-Host ("  {0,-16} {1}" -f "payment-service", $env:PAYMENT_VERSION)
Write-Host ("  {0,-16} {1}" -f "email-service",   $env:EMAIL_VERSION)
Write-Host ("  {0,-16} {1}" -f "audit-service",   $env:AUDIT_VERSION)
Write-Host ("  {0,-16} {1}" -f "mock-bank",       $env:MOCKBANK_VERSION)

docker compose up --build @args
