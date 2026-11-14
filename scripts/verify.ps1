# Local verification for FinSim contributors.
#
# Runs the same three checks CI enforces on every pull request:
#   1. Spotless formatting check (.\gradlew.bat spotlessCheck)
#   2. Gradle tests (.\gradlew.bat test)
#   3. DCO sign-off on the latest commit (git commit -s)
#
# Usage (from anywhere inside the repository):
#   .\scripts\verify.ps1
#
# The script stops at the first failing check and exits with code 1.
# Gradle output is shown as-is so failures can be investigated directly.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Always run from the repository root, wherever the script is invoked from.
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

Write-Host "== [1/3] Formatting check (.\gradlew.bat spotlessCheck) =="
& .\gradlew.bat spotlessCheck
if ($LASTEXITCODE -ne 0) {
    Write-Host "Formatting check failed." -ForegroundColor Red
    Write-Host "Run .\gradlew.bat spotlessApply to fix formatting issues." -ForegroundColor Red
    exit 1
}
Write-Host "Formatting check passed."

Write-Host "== [2/3] Tests (.\gradlew.bat test) =="
& .\gradlew.bat test
if ($LASTEXITCODE -ne 0) {
    Write-Host "Tests failed." -ForegroundColor Red
    Write-Host "Inspect the Gradle test output above to find the failing test." -ForegroundColor Red
    exit 1
}
Write-Host "Tests passed."

Write-Host "== [3/3] DCO sign-off check =="
$signedOff = git log -1 --format=%B | Select-String -Pattern '^Signed-off-by: ' -Quiet
if (-not $signedOff) {
    Write-Host "DCO sign-off is missing." -ForegroundColor Red
    Write-Host "Use git commit --amend -s to add a sign-off to your latest commit." -ForegroundColor Red
    exit 1
}
Write-Host "DCO sign-off present."

Write-Host "All local verification checks passed."
