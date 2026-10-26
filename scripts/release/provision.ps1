<#
.SYNOPSIS
  First-time Agent Eye release-signing setup: install dependencies, create the signing
  vault + key in 1Password, then verify the key can be recovered.

.DESCRIPTION
  Owner-run (1Password + Windows Hello approvals). Wraps the Node CLI and adds no logic of its own:
    1. npm ci, only when node_modules is missing
    2. cli.mjs provision      (setup-vault, then generate-key)
    3. cli.mjs verify-key
  Afterwards commit the public pin it prints:
    git add src/android/releases/signing-certificate.json
    git commit -m "Pin Agent Eye release certificate"
  Works from any directory (cmd, PowerShell 5.1 or 7, even C:\Windows\System32). The repo is located
  from this script's own path. The CLI's log (<repo>\.tmp\owner-runs\provision-<stamp>.log) is authoritative;
  this script writes a short wrapper log beside it and ends with "OK (exit 0) log: <CLI log>" or
  "FAILED (exit N) log: <CLI log>".

  Exit codes: 0 done (or dry run completed), 1 a prerequisite is missing (node or npm not found),
  2 npm ci failed, otherwise the failing CLI command's own exit code.

.PARAMETER DryRun
  Change nothing. Passes --dry-run to the CLI (no 1Password access, no subprocess calls, no
  secret reads or writes) and only prints what npm ci would do. Without it the CLI runs with --execute.
.PARAMETER Account
  Optional 1Password account for the vault (CLI --account; otherwise OP_ACCOUNT_ID, an existing config, or the default).
#>
[CmdletBinding()]
param(
  [switch]$DryRun,
  [string]$Account
)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'owner-run.ps1')
. (Join-Path $PSScriptRoot 'agent-eye.ps1')
Initialize-AgentEyeRun 'provision'
$mode = if ($DryRun) { '--dry-run' } else { '--execute' }
Write-Log "Agent Eye provision ($mode). Repo: $script:RepoRoot"

$node = Find-Tool 'node'
if (-not $node) { Complete-OwnerRun 1 'node was not found on PATH. Install Node 22 and open a new shell.' }
Write-Log "node: $node"

if (-not (Test-Path -LiteralPath (Join-Path $script:RepoRoot 'node_modules'))) {
  if ($DryRun) { Write-Log 'DRY RUN: would run npm ci (node_modules is missing).' }
  else {
    $npm = Find-Tool 'npm'
    if (-not $npm) { Complete-OwnerRun 1 'npm was not found on PATH (needed because node_modules is missing).' }
    $code = Invoke-Native $npm @('ci') $script:RepoRoot
    if ($code -ne 0) { Complete-OwnerRun 2 "npm ci failed (exit $code)." }
  }
} else { Write-Log 'node_modules present; skipping npm ci.' }

$provisionArgs = @('provision', $mode)
if ($Account) { $provisionArgs += @('--account', $Account) }
$code = Invoke-AgentEyeCli $provisionArgs
if ($code -ne 0) { Complete-AgentEyeRun $code 'provision failed.' }

$code = Invoke-AgentEyeCli @('verify-key', $mode)
if ($code -ne 0) { Complete-AgentEyeRun $code 'verify-key failed.' }

if (-not $DryRun) { Write-Log 'Next: git add src/android/releases/signing-certificate.json; git commit -m "Pin Agent Eye release certificate"' }
Complete-AgentEyeRun 0
