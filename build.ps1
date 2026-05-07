# =============================================================================
# build.ps1 -- produce a single self-contained Spring Boot jar that hosts
#              both the API and the React SPA on one port.
#
# Output: backend/target/stock-tracker-backend-0.0.1-SNAPSHOT.jar
#
# Usage (from the repo root):
#     .\build.ps1
#
# What it does:
#   1. npm install + npm run build in /frontend  -> emits frontend/dist
#   2. mvn clean package in /backend             -> fat jar that includes
#                                                    frontend/dist as static/
#                                                    on the classpath (see
#                                                    pom.xml)
# =============================================================================

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

Write-Host "==> Building React frontend" -ForegroundColor Cyan
Push-Location (Join-Path $root "frontend")
try {
    # `npm install` instead of `npm ci`: ci wipes node_modules first, which
    # frequently fails on Windows when the project lives inside OneDrive
    # because OneDrive holds transient locks on files like esbuild.exe.
    # Plain install respects package-lock.json and is much more forgiving.
    npm install
    if ($LASTEXITCODE -ne 0) {
        throw "npm install failed (often caused by OneDrive or antivirus locking files in node_modules - see DEPLOY.md troubleshooting)"
    }

    npm run build
    if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }
} finally {
    Pop-Location
}

Write-Host "==> Packaging Spring Boot jar" -ForegroundColor Cyan
Push-Location (Join-Path $root "backend")
try {
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $root "backend\target\*.jar") `
        | Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*.original" } `
        | Select-Object -First 1

Write-Host ""
Write-Host "Build complete." -ForegroundColor Green
Write-Host "  Artifact: $($jar.FullName)"
Write-Host "  Run with: .\run-prod.ps1"
