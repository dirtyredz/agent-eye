<#
.SYNOPSIS
  Sign a CI-built Agent Eye draft release locally (1Password + Windows Hello) and optionally publish it.

.DESCRIPTION
  Owner-run. A pass-through to `cli.mjs release-sign`; the CLI validates -Tag, finds the draft's numeric
  release ID with gh (refusing a published release), runs gradlew releaseTools + --stop, then signs.
  Run it from a clean checkout of the tagged commit (git switch --detach <Tag>) with an up-to-date
  origin/main; the CLI enforces that. Without -Publish the signed draft is left for review; rerunning
  with -Publish verifies again and publishes last.
  A fresh checkout needs nothing pre-installed: it runs `npm ci` (logged) when node_modules is missing, and the CLI
  falls back to the main worktree's git-ignored android/signing.local.json when the checkout has none.
  Works from any directory and PowerShell 5.1 or 7. The CLI's log (<repo>\.tmp\owner-runs\release-sign-<stamp>.log)
  is authoritative; this script writes a short wrapper log beside it, forwards the CLI's exit code, and its
  last line is "OK (exit 0) log: <CLI log>" or "FAILED (exit N) log: <CLI log>".

  Exit codes: 0 done (or dry run completed), 1 node missing (or the CLI rejected the input), otherwise the CLI's code.

.PARAMETER Tag
  Release tag, like v1.0.0. Required.
.PARAMETER ReleaseId
  Positive numeric draft database ID. Only overrides the CLI's default lookup of the draft by tag.
.PARAMETER Publish
  Publish after signing and verifying (CLI --publish).
.PARAMETER DryRun
  Change nothing: CLI --dry-run prints the plan (no gh lookup, no gradle, no 1Password).
.PARAMETER SkipReleaseTools
  Skip the gradlew releaseTools / --stop step (already resolved for this checkout).
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Tag,
  [string]$ReleaseId,
  [switch]$Publish,
  [switch]$DryRun,
  [switch]$SkipReleaseTools
)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'owner-run.ps1')
. (Join-Path $PSScriptRoot 'agent-eye.ps1')
Initialize-AgentEyeRun 'sign-release'
$mode = if ($DryRun) { '--dry-run' } else { '--execute' }
$cliArgs = @('release-sign', '--tag', $Tag, $mode)
if ($ReleaseId) { $cliArgs += @('--release-id', $ReleaseId) }
if ($Publish) { $cliArgs += '--publish' }
if ($SkipReleaseTools) { $cliArgs += '--skip-release-tools' }
Write-Log "Agent Eye sign-release $Tag ($mode). Repo: $script:RepoRoot"

# A fresh clean checkout has no node_modules and the CLI cannot load without it, so install first (also for -DryRun).
Install-AgentEyeDependencies

$code = Invoke-AgentEyeCli $cliArgs
Complete-AgentEyeRun $code
