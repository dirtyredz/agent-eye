<#
.SYNOPSIS
  Inspect the Agent Eye items in the signing vault, or remove orphans left by a failed provision.

.DESCRIPTION
  Owner-run (1Password + Windows Hello approval). Wraps the Node CLI and adds no logic of its own.
    -Inspect          cli.mjs inspect-vault: read-only list of Agent Eye item titles, categories, states, created times (never values).
    -CleanupOrphans   cli.mjs cleanup-orphans: refuses unless the public pin sha256 is null and gh confirms no GitHub release exists
                      (fails closed if gh fails). Deletes only the two exact Agent Eye item titles of the configured vault.
                      Without -Execute it is a dry run that prints the plan and deletes nothing.
  Exactly one of -Inspect / -CleanupOrphans is required. After a cleanup, rerun provision.ps1.
  Works from any directory. Each run writes <repo>\.tmp\owner-runs\vault-repair-<stamp>-<pid>.log and ends with
  "OK (exit 0) log: <path>" or "FAILED (exit N) log: <path>".

  Exit codes: 0 done, 1 bad arguments or node missing, 3 the CLI command failed.

.PARAMETER Execute
  With -CleanupOrphans: actually delete. With -Inspect: ignored (inspect always reads).
.PARAMETER DryRun
  Print the CLI plan only (no 1Password access).
#>
[CmdletBinding()]
param(
  [switch]$Inspect,
  [switch]$CleanupOrphans,
  [switch]$Execute,
  [switch]$DryRun
)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'owner-run.ps1')
Initialize-OwnerRun 'vault-repair'

if ($Inspect -eq $CleanupOrphans) { Complete-OwnerRun 1 'Pass exactly one of -Inspect or -CleanupOrphans.' }
if ($DryRun -and $Execute) { Complete-OwnerRun 1 '-DryRun and -Execute conflict.' }
$command = if ($Inspect) { 'inspect-vault' } else { 'cleanup-orphans' }
$run = if ($DryRun) { $false } elseif ($Inspect) { $true } else { [bool]$Execute }
$mode = if ($run) { '--execute' } else { '--dry-run' }
Write-Log "Agent Eye $command ($mode). Repo: $script:RepoRoot"
if ($CleanupOrphans -and -not $run -and -not $DryRun) { Write-Log 'No -Execute: showing the plan only; nothing will be deleted.' }

$node = Find-Tool 'node'
if (-not $node) { Complete-OwnerRun 1 'node was not found on PATH. Install Node 22 and open a new shell.' }
$code = Invoke-Native $node @($script:CliPath, $command, $mode) $script:RepoRoot
if ($code -ne 0) { Complete-OwnerRun 3 "$command failed (exit $code)." }
Complete-OwnerRun 0
