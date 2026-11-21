<#
.SYNOPSIS
  Prepare a fresh git worktree for `node --test`: run `npm ci` when node_modules is missing.

.DESCRIPTION
  A new worktree has no node_modules, so the first `node --test` fails the tests that import sharp
  (icon-assets and the os.tmpdir() entry-point tests, ERR_MODULE_NOT_FOUND; dk-4258d0f3). Run this once
  per worktree before the fast gate. Does nothing when node_modules already exists.

  Works from any directory; the repo is located from this script's path. Each run writes
  <repo>\.tmp\owner-runs\setup-worktree-<stamp>-<pid>.log (git-ignored) and ends with
  "OK (exit 0) log: <path>" or "FAILED (exit N) log: <path>".

  Exit codes: 0 done (or dry run completed), 1 npm not found, 2 npm ci failed.

.PARAMETER DryRun
  Change nothing; log what would run.
#>
[CmdletBinding()]
param([switch]$DryRun)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\release\owner-run.ps1')
Initialize-OwnerRun 'setup-worktree'
$mode = if ($DryRun) { 'dry run' } else { 'execute' }
Write-Log "Agent Eye worktree setup ($mode). Repo: $script:RepoRoot"

if (Test-Path -LiteralPath (Join-Path $script:RepoRoot 'node_modules')) {
  Write-Log 'node_modules present; nothing to do.'
  Complete-OwnerRun 0
}
if ($DryRun) {
  Write-Log 'DRY RUN: would run npm ci (node_modules is missing).'
  Complete-OwnerRun 0
}
$npm = Find-Tool 'npm'
if (-not $npm) { Complete-OwnerRun 1 'npm was not found on PATH.' }
Write-Log 'node_modules missing; running npm ci.'
$code = Invoke-Native $npm @('ci') $script:RepoRoot
if ($code -ne 0) { Complete-OwnerRun 2 "npm ci failed (exit $code)." }
Complete-OwnerRun 0
