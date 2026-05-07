# =============================================================================
# install-service.ps1 -- register the stock-tracker fat jar as a Windows
# service via NSSM. Reads secrets from .env and writes them into the
# service environment so the daemon never depends on the calling user's
# `setx` history. Must run as Administrator.
#
# Usage (elevated PowerShell, from the repo root):
#     .\install-service.ps1
#     .\install-service.ps1 -ServiceName MyStocks  -Port 9000
#
# To remove later:                nssm remove StockTracker confirm
# To restart after a rebuild:     nssm restart StockTracker
# =============================================================================

[CmdletBinding()]
param(
    [string]$ServiceName = "StockTracker",
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

# --- Admin check ------------------------------------------------------------
$current = [Security.Principal.WindowsPrincipal]::new(
    [Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $current.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "This script must be run from an elevated (Administrator) PowerShell." -ForegroundColor Red
    exit 1
}

# --- Tooling check ----------------------------------------------------------
foreach ($tool in @("nssm", "java")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Host "Could not find '$tool' on PATH. Install it first." -ForegroundColor Red
        if ($tool -eq "nssm") {
            Write-Host "  winget install --id NSSM.NSSM"
        }
        exit 1
    }
}

# --- Locate the jar ---------------------------------------------------------
$jar = Get-ChildItem (Join-Path $root "backend\target\*.jar") -ErrorAction SilentlyContinue `
        | Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*.original" } `
        | Select-Object -First 1
if (-not $jar) {
    Write-Host "No jar found in backend\target. Run .\build.ps1 first." -ForegroundColor Red
    exit 1
}

# --- Read .env into a hashtable --------------------------------------------
function Read-DotEnv {
    param([string]$Path)
    $map = [ordered]@{}
    if (-not (Test-Path $Path)) { return $map }
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
        $map[$key] = $val
    }
    return $map
}

$envMap = Read-DotEnv (Join-Path $root ".env")
foreach ($required in @("DB_PASSWORD", "JWT_SECRET")) {
    if (-not $envMap[$required]) {
        Write-Host "Missing $required in .env (see .env.example)." -ForegroundColor Red
        exit 1
    }
}

# --- (Re)install the service -----------------------------------------------
$java = (Get-Command java).Source

$existing = & nssm status $ServiceName 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Service '$ServiceName' already exists. Removing first..." -ForegroundColor Yellow
    & nssm stop   $ServiceName 2>$null | Out-Null
    & nssm remove $ServiceName confirm | Out-Null
}

Write-Host "==> Installing service '$ServiceName'" -ForegroundColor Cyan
& nssm install $ServiceName $java "-Dserver.port=$Port -jar `"$($jar.FullName)`""
& nssm set     $ServiceName AppDirectory $root
& nssm set     $ServiceName Start SERVICE_AUTO_START
& nssm set     $ServiceName Description "Stock Tracker (Spring Boot fat jar)"

# Logs - rotated at 10 MB so stdout doesn't fill the disk over time.
$logDir = "C:\ProgramData\StockTracker"
New-Item -ItemType Directory -Force $logDir | Out-Null
& nssm set $ServiceName AppStdout       (Join-Path $logDir "stdout.log")
& nssm set $ServiceName AppStderr       (Join-Path $logDir "stderr.log")
& nssm set $ServiceName AppRotateFiles  1
& nssm set $ServiceName AppRotateBytes  10485760

# Environment - NSSM expects KEY=VALUE pairs joined by NULs in a single
# string, so we build it explicitly from the .env hashtable.
$pairs = $envMap.Keys | ForEach-Object { "$_=$($envMap[$_])" }
$envBlock = [string]::Join([char]0, $pairs)
& nssm set $ServiceName AppEnvironmentExtra $envBlock

Write-Host "==> Starting service" -ForegroundColor Cyan
& nssm start $ServiceName | Out-Null

Start-Sleep -Seconds 2
$status = & nssm status $ServiceName
Write-Host ""
Write-Host "Service '$ServiceName' status: $status" -ForegroundColor Green
Write-Host "  Logs:  $logDir\stdout.log"
Write-Host "  URL:   http://localhost:$Port"
Write-Host ""
Write-Host "After rebuilding the jar, restart with:  nssm restart $ServiceName"
