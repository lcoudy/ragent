param(
    [string]$RepoRoot = $PSScriptRoot,
    [string]$MainBranch = "main",
    [string]$QueueBranch = "contribution-queue",
    [string]$CheckCommand = "",
    [switch]$DryRun,
    [switch]$NoFetch,
    [switch]$NoPull,
    [switch]$NoPush
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)

    & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-GitOutput {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)

    $output = & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE"
    }
    return $output
}

function Assert-CleanWorkingTree {
    $status = @(Get-GitOutput status --porcelain)
    if ($status.Count -gt 0) {
        throw "Working tree is not clean. Commit, stash, or clean local changes before publishing a queued commit."
    }
}

function Get-CurrentBranch {
    $branch = Get-GitOutput branch --show-current
    if ($branch -is [array]) {
        return $branch[0]
    }
    return $branch
}

function Get-NextQueuedCommit {
    $commits = @(Get-GitOutput rev-list --reverse "${MainBranch}..${QueueBranch}")
    if ($commits.Count -eq 0) {
        return $null
    }
    return $commits[0]
}

function Invoke-CheckCommand {
    param([string]$Command)

    if ([string]::IsNullOrWhiteSpace($Command)) {
        Write-Host "No check command configured; skipping checks."
        return
    }

    Write-Host "Running check command: $Command"
    & powershell -NoProfile -ExecutionPolicy Bypass -Command $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Check command failed with exit code $LASTEXITCODE"
    }
}

Set-Location $RepoRoot

Invoke-Git rev-parse --show-toplevel | Out-Null

$originalBranch = Get-CurrentBranch

try {
    $branches = @(Get-GitOutput branch --list $QueueBranch)
    if ($branches.Count -eq 0) {
        throw "Queue branch '$QueueBranch' was not found. Create it and add one meaningful commit per queued contribution."
    }

    $mainBranches = @(Get-GitOutput branch --list $MainBranch)
    if ($mainBranches.Count -eq 0) {
        throw "Main branch '$MainBranch' was not found."
    }

    if ($DryRun) {
        $nextCommit = Get-NextQueuedCommit
        if (-not $nextCommit) {
            Write-Host "No queued commits left in ${MainBranch}..${QueueBranch}."
            exit 0
        }

        $subject = Get-GitOutput log -1 --pretty=format:%s $nextCommit
        Write-Host "Next queued commit: $nextCommit $subject"
        Write-Host "DryRun only: no fetch, pull, checkout, cherry-pick, check, or push was performed."
        exit 0
    }

    Assert-CleanWorkingTree

    if (-not $NoFetch) {
        Invoke-Git fetch --all --prune
    }

    Invoke-Git checkout $MainBranch

    if (-not $NoPull) {
        Invoke-Git pull --ff-only
    }

    Assert-CleanWorkingTree

    $nextCommit = Get-NextQueuedCommit
    if (-not $nextCommit) {
        Write-Host "No queued commits left in ${MainBranch}..${QueueBranch}."
        exit 0
    }

    $subject = Get-GitOutput log -1 --pretty=format:%s $nextCommit
    Write-Host "Next queued commit: $nextCommit $subject"

    Invoke-Git cherry-pick $nextCommit

    try {
        Invoke-CheckCommand -Command $CheckCommand
    } catch {
        Write-Host "Check failed; aborting cherry-pick."
        Invoke-Git cherry-pick --abort
        throw
    }

    if ($NoPush) {
        Write-Host "NoPush enabled. Commit was cherry-picked locally but not pushed."
    } else {
        Invoke-Git push
    }

    Write-Host "Published queued commit: $nextCommit"
} catch {
    Write-Error $_
    exit 1
} finally {
    if ($DryRun -and $originalBranch -and (Get-CurrentBranch) -ne $originalBranch) {
        Invoke-Git checkout $originalBranch
    }
}
