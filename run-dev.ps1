# =============================================================================
# run-dev.ps1 -- start the backend in dev mode (mvn spring-boot:run) with
# secrets loaded from .env. Mirrors run-prod.ps1 so you only ever maintain
# one source of truth for DB_PASSWORD / JWT_SECRET.
#
# Usage (from anywhere - the script always cd's to its own folder):
#     .\run-dev.ps1
#
# This is a drop-in replacement for `mvn spring-boot:run`. The Spring Boot
# Maven plugin handles file watching / reload; the React dev server still
# lives separately at `npm run dev` in /frontend (Vite proxies /api to :8080).
# =============================================================================

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

# --- Load .env (same minimal parser as run-prod.ps1) ------------------------
function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $eq = $trimmed.IndexOf("=")
        if ($eq -lt 1) { continue }
        $key = $trimmed.Substring(0, $eq).Trim()
        $val = $trimmed.Substring($eq + 1).Trim()
        if ($val.Length -ge 2 -and (
            ($val.StartsWith('"') -and $val.EndsWith('"')) -or
            ($val.StartsWith("'") -and $val.EndsWith("'"))
        )) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        if (-not (Get-Item "Env:$key" -ErrorAction SilentlyContinue)) {
            Set-Item -Path "Env:$key" -Value $val
        }
    }
}

Import-DotEnv (Join-Path $root ".env")

$missing = @()
if (-not $env:DB_PASSWORD) { $missing += "DB_PASSWORD" }
if (-not $env:JWT_SECRET)  { $missing += "JWT_SECRET" }
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host ("Missing required env var(s): {0}" -f ($missing -join ", ")) -ForegroundColor Red
    Write-Host "Either set them in $root\.env (see .env.example) or export them in your shell." -ForegroundColor Red
    exit 1
}

# --- Hand off to the Spring Boot Maven plugin -------------------------------
Set-Location (Join-Path $root "backend")
Write-Host "==> mvn spring-boot:run (with .env loaded)" -ForegroundColor Cyan
mvn spring-boot:run
