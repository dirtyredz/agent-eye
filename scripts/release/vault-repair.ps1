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
  Works from any directory. The CLI's log is authoritative; this script keeps a short wrapper log and ends with
  "OK (exit 0) log: <CLI log>" or "FAILED (exit N) log: <CLI log>".

  Exit codes: 0 done, 1 bad arguments or node missing, otherwise the CLI's exit code.

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
. (Join-Path $PSScriptRoot 'agent-eye.ps1')
Initialize-AgentEyeRun 'vault-repair'

if ($Inspect -eq $CleanupOrphans) { Complete-OwnerRun 1 'Pass exactly one of -Inspect or -CleanupOrphans.' }
if ($DryRun -and $Execute) { Complete-OwnerRun 1 '-DryRun and -Execute conflict.' }
$command = if ($Inspect) { 'inspect-vault' } else { 'cleanup-orphans' }
$run = if ($DryRun) { $false } elseif ($Inspect) { $true } else { [bool]$Execute }
$mode = if ($run) { '--execute' } else { '--dry-run' }
Write-Log "Agent Eye $command ($mode). Repo: $script:RepoRoot"
if ($CleanupOrphans -and -not $run -and -not $DryRun) { Write-Log 'No -Execute: showing the plan only; nothing will be deleted.' }

$code = Invoke-AgentEyeCli @($command, $mode)
Complete-AgentEyeRun $code
