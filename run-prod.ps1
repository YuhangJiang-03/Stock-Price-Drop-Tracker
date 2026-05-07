# =============================================================================
# run-prod.ps1 -- launch the production jar built by build.ps1.
#
# Usage (from anywhere - the script always cd's to its own folder):
#     .\run-prod.ps1                    # uses port 8080
#     .\run-prod.ps1 -Port 9000         # custom port
#
# Reads DB_PASSWORD and JWT_SECRET from a .env file in the repo root, with
# real environment variables taking precedence so a developer can override
# any key for a single shell with `$env:DB_PASSWORD = "..."`.
# =============================================================================

[CmdletBinding()]
param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

# --- Load .env --------------------------------------------------------------
# Minimal parser: KEY=VALUE per line, # for comments, optional matching
# single or double quotes around the value. Real env vars win - we only
# fill in keys that aren't already set so a one-shot `$env:KEY=...` in the
# shell still overrides .env.
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
        # Strip a single matched pair of surrounding quotes.
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

# --- Sanity-check the required values ---------------------------------------
$missing = @()
if (-not $env:DB_PASSWORD) { $missing += "DB_PASSWORD" }
if (-not $env:JWT_SECRET)  { $missing += "JWT_SECRET" }
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host ("Missing required env var(s): {0}" -f ($missing -join ", ")) -ForegroundColor Red
    Write-Host "Either set them in $root\.env (see .env.example) or export them in your shell." -ForegroundColor Red
    exit 1
}

# --- Locate the fat jar -----------------------------------------------------
$jar = Get-ChildItem (Join-Path $root "backend\target\*.jar") -ErrorAction SilentlyContinue `
        | Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*.original" } `
        | Select-Object -First 1

if (-not $jar) {
    Write-Host "No jar found in backend\target. Run .\build.ps1 first." -ForegroundColor Red
    exit 1
}

Write-Host "==> Starting Stock Tracker on http://localhost:$Port" -ForegroundColor Cyan
Write-Host "    Artifact: $($jar.FullName)"
Write-Host ""

java "-Dserver.port=$Port" -jar $jar.FullName
