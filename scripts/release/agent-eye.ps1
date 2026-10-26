# Agent Eye specifics for the owner-run scripts (dot-source after owner-run.ps1; defines functions only).
# owner-run.ps1 is the canonical harness template copied verbatim (v1.0.0); everything repo-specific lives here.
#
# Boundary: the Node CLI (src/android/releases/cli/cli.mjs) owns every release decision and its own log
# (<repo>\.tmp\owner-runs\<command>-<stamp>.log), which is authoritative. This layer keeps a short wrapper log,
# forwards the CLI's exit code, and ends with ONE status line that points at the CLI's log. The CLI's own
# verdict line is recorded in the wrapper log but never printed, so the console never shows two verdicts.

function Initialize-AgentEyeRun([string]$Name) {
  Initialize-OwnerRun $Name
  $script:CliPath = Join-Path $script:RepoRoot 'src/android/releases/cli/cli.mjs'
  $script:CliLog = $null
  foreach ($variable in 'GH_TOKEN', 'GITHUB_TOKEN', 'OP_SERVICE_ACCOUNT_TOKEN') {
    Add-LogSecret ([Environment]::GetEnvironmentVariable($variable))
  }
}

# Runs the Node CLI; returns its exit code and remembers the log path it reported.
function Invoke-AgentEyeCli([string[]]$CliArgs) {
  $node = Find-Tool 'node'
  if (-not $node) { Complete-OwnerRun 1 'node was not found on PATH. Install Node 22 and open a new shell.' }
  Write-Log ('> node {0} {1}' -f $script:CliPath, ($CliArgs -join ' '))
  $script:CliLog = $null
  $prevEap = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'   # Windows PowerShell 5.1 turns native stderr into errors under 'Stop'
  Push-Location -LiteralPath $script:RepoRoot
  try {
    & $node $script:CliPath @CliArgs 2>&1 | ForEach-Object {
      $line = [string]$_
      if ($line -match '^(OK|FAILED) \(exit (\d+)\) log: (.+)$') {
        $script:CliLog = $Matches[3].Trim()
        Add-Content -LiteralPath $script:LogFile -Value ('CLI verdict (not echoed): ' + (Hide-Secrets $line)) -Encoding UTF8
      } else { Write-Log $line }
    }
    return [int]$LASTEXITCODE
  } finally {
    Pop-Location
    $ErrorActionPreference = $prevEap
  }
}

# Final status line for a run that invoked the CLI: same exit code, CLI log path. Falls back to the
# wrapper log when the CLI never ran far enough to report one.
function Complete-AgentEyeRun([int]$Code, [string]$Reason) {
  if (-not $script:CliLog) { Complete-OwnerRun $Code $Reason }
  if ($Reason) { Write-Log "STOPPED: $Reason" }
  Write-Log "Wrapper log: $($script:LogFile)"
  $final = if ($Code -eq 0) { "OK (exit 0) log: $($script:CliLog)" } else { "FAILED (exit $Code) log: $($script:CliLog)" }
  Add-Content -LiteralPath $script:LogFile -Value $final -Encoding UTF8
  Write-Host $final
  exit $Code
}
