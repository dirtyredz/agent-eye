<#
  Runs provision.ps1 and sign-release.ps1 with -DryRun from the temp directory (not the repo) and asserts exit codes,
  the final OK/FAILED line and the log file. Plain PowerShell asserts, no Pester. Run:
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\release\release-scripts.test.ps1
  Also works under pwsh (Linux CI). Exit code = number of failures.
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$shell = (Get-Process -Id $PID).Path
$tmp = [System.IO.Path]::GetTempPath()
$failures = 0

function Invoke-Case([string]$Title, [string]$Script, [string[]]$Arguments, [int]$ExpectExit, [string]$ExpectKind, [string]$ExpectLogLike = '') {
  $file = Join-Path $PSScriptRoot $Script
  Push-Location -LiteralPath $tmp
  try { $out = @(& $shell -NoProfile -ExecutionPolicy Bypass -File $file @Arguments 2>&1 | ForEach-Object { [string]$_ }) }
  finally { Pop-Location }
  $code = $LASTEXITCODE
  $last = ($out | Where-Object { $_ -match '\S' } | Select-Object -Last 1)
  $problems = @()
  if ($code -ne $ExpectExit) { $problems += "exit $code, expected $ExpectExit" }
  $verdicts = @($out | Where-Object { $_ -match '^(OK|FAILED) \(exit \d+\) log: ' }).Count
  if ($verdicts -ne 1) { $problems += "$verdicts verdict lines, expected exactly 1" }
  if ($last -notmatch ('^{0} \(exit (\d+)\) log: (.+)$' -f $ExpectKind)) { $problems += "last line: $last" }
  else {
    if ([int]$Matches[1] -ne $ExpectExit) { $problems += "reported exit $($Matches[1])" }
    $log = $Matches[2].Trim()
    if (-not (Test-Path -LiteralPath $log)) { $problems += "log missing: $log" }
    elseif ($log -notmatch 'owner-runs') { $problems += "log not under owner-runs: $log" }
    elseif ($ExpectLogLike -and ((Split-Path -Leaf $log) -notlike $ExpectLogLike)) { $problems += "final line should point at the CLI log ($ExpectLogLike): $log" }
  }
  if ($problems.Count) { $script:failures++; Write-Host "FAIL: $Title -> $($problems -join '; ')" -ForegroundColor Red; $out | ForEach-Object { Write-Host "  $_" } }
  else { Write-Host "PASS: $Title" -ForegroundColor Green }
}

Invoke-Case 'provision -DryRun' 'provision.ps1' @('-DryRun') 0 'OK' 'verify-key-*'
Invoke-Case 'vault-repair -Inspect -DryRun' 'vault-repair.ps1' @('-Inspect', '-DryRun') 0 'OK' 'inspect-vault-*'
Invoke-Case 'vault-repair -CleanupOrphans (no -Execute) is a dry run' 'vault-repair.ps1' @('-CleanupOrphans') 0 'OK' 'cleanup-orphans-*'
Invoke-Case 'vault-repair requires exactly one action' 'vault-repair.ps1' @() 1 'FAILED'
Invoke-Case 'vault-repair rejects both actions' 'vault-repair.ps1' @('-Inspect', '-CleanupOrphans') 1 'FAILED'
Invoke-Case 'sign-release -DryRun with release id' 'sign-release.ps1' @('-Tag', 'v1.0.0', '-ReleaseId', '123', '-DryRun', '-Publish') 0 'OK' 'release-sign-*'
Invoke-Case 'sign-release -DryRun without release id (no gh lookup)' 'sign-release.ps1' @('-Tag', 'v1.0.1', '-DryRun') 0 'OK' 'release-sign-*'
Invoke-Case 'sign-release rejects a malformed tag (CLI exit forwarded)' 'sign-release.ps1' @('-Tag', '1.0', '-DryRun') 1 'FAILED' 'release-sign-*'
Invoke-Case 'sign-release rejects a bad release id' 'sign-release.ps1' @('-Tag', 'v1.0.0', '-ReleaseId', 'abc', '-DryRun') 1 'FAILED' 'release-sign-*'

Write-Host "`nFailures: $failures"
exit $failures
