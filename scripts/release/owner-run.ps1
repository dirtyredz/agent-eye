# Shared plumbing for the owner-run scripts in this folder (dot-source it; it defines functions only).
# Responsibilities: repo root from $PSScriptRoot, timestamped log under <repo>\.tmp\owner-runs\,
# tool lookup, child-process capture, and the final "OK (exit 0) log: <path>" / "FAILED (exit N) log: <path>" line.
# It never reimplements the release logic: the Node CLI (src/android/releases/cli/cli.mjs) does the work.

function Initialize-OwnerRun([string]$Name) {
  $script:RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
  $dir = Join-Path $script:RepoRoot '.tmp/owner-runs'
  New-Item -ItemType Directory -Path $dir -Force | Out-Null
  $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
  $script:LogFile = Join-Path $dir "$Name-$stamp-$PID.log"
  $script:CliPath = Join-Path $script:RepoRoot 'src/android/releases/cli/cli.mjs'
}

function Write-Log([string]$Message) {
  $line = '{0} {1}' -f (Get-Date -Format 'HH:mm:ss'), $Message
  Write-Host $line
  Add-Content -LiteralPath $script:LogFile -Value $line -Encoding UTF8
}

# Ends the run: prints exactly one final status line (also appended to the log) and exits with $Code.
function Complete-OwnerRun([int]$Code, [string]$Reason) {
  if ($Reason) { Write-Log "STOPPED: $Reason" }
  $final = if ($Code -eq 0) { "OK (exit 0) log: $($script:LogFile)" } else { "FAILED (exit $Code) log: $($script:LogFile)" }
  Add-Content -LiteralPath $script:LogFile -Value $final -Encoding UTF8
  Write-Host $final
  exit $Code
}

function Find-Tool([string]$Name) {
  $cmd = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($cmd) { return $cmd.Source }
  return $null
}

# Runs a native command with output teed to the console and the log; returns its exit code.
function Invoke-Native([string]$Exe, [string[]]$Arguments, [string]$WorkingDirectory) {
  Write-Log ('> {0} {1}' -f $Exe, ($Arguments -join ' '))
  $prevEap = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'   # Windows PowerShell 5.1 turns native stderr into errors under 'Stop'
  Push-Location -LiteralPath $WorkingDirectory
  try {
    & $Exe @Arguments 2>&1 | ForEach-Object { Write-Log ([string]$_) }
    return [int]$LASTEXITCODE
  } finally {
    Pop-Location
    $ErrorActionPreference = $prevEap
  }
}
