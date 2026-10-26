<#
.SYNOPSIS
  Sign a CI-built Agent Eye draft release locally (1Password + Windows Hello) and optionally publish it.

.DESCRIPTION
  Owner-run. Wraps the Node CLI's release-sign and adds no signing logic. Steps:
    1. validate -Tag (vMAJOR.MINOR.PATCH)
    2. find the draft's numeric release ID with gh (unless -ReleaseId is given)
    3. gradlew releaseTools, then gradlew --stop (pinned verification tools; skip with -SkipReleaseTools)
    4. cli.mjs release-sign --tag <Tag> --release-id <id> [--publish]
  Run it from a clean checkout of the tagged commit (git switch --detach <Tag>) with an up-to-date
  origin/main; the CLI enforces that. Without -Publish the signed draft is left for review; rerunning
  with -Publish verifies again and publishes last.
  Works from any directory and PowerShell 5.1 or 7. Log: <repo>\.tmp\owner-runs\sign-release-<stamp>-<pid>.log;
  the last line is "OK (exit 0) log: <path>" or "FAILED (exit N) log: <path>".

  Exit codes: 0 done (or dry run completed), 1 bad input or a prerequisite is missing (tag format,
  node, gh), 2 draft lookup failed or the release is not a draft, 3 releaseTools failed,
  4 release-sign failed.

.PARAMETER Tag
  Release tag, like v1.0.0. Required.
.PARAMETER ReleaseId
  Positive numeric draft database ID. Default: looked up with gh release view <Tag>.
.PARAMETER Publish
  Publish after signing and verifying (CLI --publish).
.PARAMETER DryRun
  Change nothing: no gh lookup, no gradle, CLI --dry-run (a missing -ReleaseId is shown as a placeholder).
.PARAMETER SkipReleaseTools
  Skip the gradlew releaseTools / --stop step (already resolved for this checkout).
.PARAMETER Repository
  GitHub repository for the draft lookup (default dirtyredz/agent-eye).
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Tag,
  [string]$ReleaseId,
  [switch]$Publish,
  [switch]$DryRun,
  [switch]$SkipReleaseTools,
  [string]$Repository = 'dirtyredz/agent-eye'
)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'owner-run.ps1')
Initialize-OwnerRun 'sign-release'
$mode = if ($DryRun) { '--dry-run' } else { '--execute' }
$pubText = if ($Publish) { ' --publish' } else { '' }
Write-Log "Agent Eye sign-release $Tag ($mode$pubText). Repo: $script:RepoRoot"

if ($Tag -notmatch '^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') { Complete-OwnerRun 1 "Tag '$Tag' must look like v1.0.0." }
if ($ReleaseId -and $ReleaseId -notmatch '^[1-9]\d*$') { Complete-OwnerRun 1 "ReleaseId '$ReleaseId' must be a positive number." }
$node = Find-Tool 'node'
if (-not $node) { Complete-OwnerRun 1 'node was not found on PATH. Install Node 22 and open a new shell.' }

if (-not $ReleaseId) {
  if ($DryRun) { $ReleaseId = '1'; Write-Log "DRY RUN: would look up the draft ID for $Tag with gh (using placeholder 1)." }
  else {
    $gh = Find-Tool 'gh'
    if (-not $gh) { Complete-OwnerRun 1 'gh was not found on PATH (needed to look up the draft ID; or pass -ReleaseId).' }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $json = (& $gh release view $Tag --repo $Repository --json databaseId,isDraft 2>&1 | Out-String)
    $ghCode = $LASTEXITCODE
    $ErrorActionPreference = $prev
    if ($ghCode -ne 0) { Complete-OwnerRun 2 "gh release view $Tag failed: $json" }
    $info = $json | ConvertFrom-Json
    if (-not $info.isDraft) { Complete-OwnerRun 2 "Release $Tag is not a draft (already published?). Nothing to sign." }
    $ReleaseId = [string]$info.databaseId
    Write-Log "Draft release ID for ${Tag}: $ReleaseId"
  }
}

if ($DryRun -or $SkipReleaseTools) { Write-Log 'Skipping gradle releaseTools.' }
else {
  $gradle = Join-Path $script:RepoRoot 'android\gradlew.bat'
  $androidDir = Join-Path $script:RepoRoot 'android'
  $code = Invoke-Native $gradle @('-p', $androidDir, 'releaseTools') $script:RepoRoot
  if ($code -ne 0) { Complete-OwnerRun 3 "gradlew releaseTools failed (exit $code)." }
  $null = Invoke-Native $gradle @('--stop') $script:RepoRoot
}

$signArgs = @($script:CliPath, 'release-sign', '--tag', $Tag, '--release-id', $ReleaseId, $mode)
if ($Publish) { $signArgs += '--publish' }
$code = Invoke-Native $node $signArgs $script:RepoRoot
if ($code -ne 0) { Complete-OwnerRun 4 "release-sign failed (exit $code)." }
Complete-OwnerRun 0
