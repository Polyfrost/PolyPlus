<#
.SYNOPSIS
    Collects thread dumps and logs from a hung Minecraft process launched by OneClient.

.DESCRIPTION
    Run this while the game is frozen. It finds the game's JVM, takes several thread dumps a
    few seconds apart, copies the game and launcher logs, and zips everything to the desktop.

    A hang before the game window appears happens during loader/mixin/mod init, which is all on
    the "main" thread. If "main" has the same stack in every dump, that stack is the freeze.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File debug-freeze.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File debug-freeze.ps1 -Minidump
    Also writes a full native memory dump (large, needs an elevated prompt). Use when the hang
    is in native code - the JVM dumps show those frames only as "native method".
#>
#requires -Version 5.1
[CmdletBinding()]
param(
    [int]$Dumps = 3,
    [int]$IntervalSeconds = 5,
    [int]$WaitSeconds = 120,
    [switch]$Minidump,
    [string]$OutDir = (Join-Path ([Environment]::GetFolderPath('Desktop')) ('polyplus-freeze-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
)

$ErrorActionPreference = 'Stop'

function Write-Step($message) { Write-Host "==> $message" -ForegroundColor Cyan }
function Write-Warn($message) { Write-Host "  ! $message" -ForegroundColor Yellow }

function Get-LauncherDirs {
    Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA 'Polyfrost') -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'data' } |
        Where-Object { Test-Path $_ }
}

function Get-GameProcess {
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    do {
        $candidates = Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" |
            Where-Object { $_.CommandLine -and $_.CommandLine -match 'KnotClient|net\.fabricmc|net\.minecraft\.client' }

        if ($candidates) {
            $oneclient = @($candidates | Where-Object { $_.CommandLine -match 'OneClient' })
            if ($oneclient.Count -gt 0) { return $oneclient[0] }
            return ($candidates | Sort-Object CreationDate -Descending)[0]
        }

        if ((Get-Date) -lt $deadline) {
            Write-Host '  waiting for the game to start...' -ForegroundColor DarkGray
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    return $null
}

function Find-Jcmd($process) {
    if ($process.ExecutablePath) {
        $sibling = Join-Path (Split-Path $process.ExecutablePath -Parent) 'jcmd.exe'
        if (Test-Path $sibling) { return $sibling }
        Write-Host "  the game's own runtime is a JRE (no jcmd next to $($process.ExecutablePath))" -ForegroundColor DarkGray
    }

    $roots = @()
    $roots += Get-LauncherDirs
    $roots += Join-Path $env:LOCALAPPDATA 'Polyfrost'
    $roots += $env:JAVA_HOME
    $roots += "$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Zulu",
              "$env:ProgramFiles\Microsoft\jdk", "${env:ProgramFiles(x86)}\Java",
              (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium')

    foreach ($root in ($roots | Where-Object { $_ } | Select-Object -Unique)) {
        if (Test-Path $root) {
            $found = Get-ChildItem -Path $root -Filter 'jcmd.exe' -Recurse -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if ($found) { return $found.FullName }
        }
    }

    $onPath = Get-Command 'jcmd.exe' -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    return $null
}

function Install-Jdk {
    if (-not (Get-Command 'winget.exe' -ErrorAction SilentlyContinue)) {
        Write-Warn 'No JDK on this machine and winget is unavailable. Install one from adoptium.net, then re-run.'
        return $null
    }

    Write-Host '  No JDK found. A JDK is needed to read the frozen game''s threads.'
    if ((Read-Host '  Install Temurin 21 now? [Y/n]') -match '^(n|no)$') { return $null }

    Write-Step 'Installing Temurin 21 (this takes a minute)'
    & winget.exe install -e --id EclipseAdoptium.Temurin.21.JDK `
        --accept-package-agreements --accept-source-agreements --disable-interactivity | Out-Host

    foreach ($root in @("$env:ProgramFiles\Eclipse Adoptium", (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium'))) {
        if (Test-Path $root) {
            $found = Get-ChildItem -Path $root -Filter 'jcmd.exe' -Recurse -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if ($found) { return $found.FullName }
        }
    }
    Write-Warn 'The install finished but jcmd.exe is still not there.'
    return $null
}

function Get-GameDir($commandLine) {
    if ($commandLine -match '--gameDir\s+(?:"([^"]+)"|(\S+))') {
        if ($matches[1]) { return $matches[1] } else { return $matches[2] }
    }
    return $null
}

function Copy-Tail($source, $destination, $lines = 600) {
    if (Test-Path $source) {
        Get-Content -Path $source -Tail $lines -ErrorAction SilentlyContinue |
            Set-Content -Path $destination -Encoding UTF8
        return $true
    }
    return $false
}

Write-Step 'Looking for the frozen game'
$game = Get-GameProcess
if (-not $game) {
    Write-Warn 'No Minecraft JVM found. Start the game, let it freeze, then run this again.'
    exit 1
}
$gamePid = $game.ProcessId
Write-Host "  pid $gamePid  ($($game.Name))"

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

$jcmd = Find-Jcmd $game
if (-not $jcmd) { $jcmd = Install-Jdk }
if ($jcmd) {
    Write-Step "Taking $Dumps thread dumps, $IntervalSeconds s apart"
    Write-Host "  using $jcmd" -ForegroundColor DarkGray
    for ($i = 1; $i -le $Dumps; $i++) {
        $target = Join-Path $OutDir ("threaddump-$i.txt")
        & $jcmd $gamePid Thread.print -l 2>&1 | Set-Content -Path $target -Encoding UTF8
        Write-Host "  dump $i -> $(Split-Path $target -Leaf)"
        if ($i -lt $Dumps) { Start-Sleep -Seconds $IntervalSeconds }
    }
} else {
    Write-Warn 'No thread dumps. Logs are still collected, but they will say much less.'
}

if ($Minidump) {
    Write-Step 'Writing a full native memory dump'
    $dumpPath = Join-Path $OutDir 'native.dmp'
    try {
        & rundll32.exe 'C:\Windows\System32\comsvcs.dll' MiniDump $gamePid $dumpPath full
        if (Test-Path $dumpPath) {
            Write-Host "  $([math]::Round((Get-Item $dumpPath).Length / 1MB)) MB -> native.dmp"
        } else {
            Write-Warn 'MiniDump wrote nothing. Re-run this script as administrator.'
        }
    } catch {
        Write-Warn "MiniDump failed: $($_.Exception.Message)"
    }
}

Write-Step 'Collecting logs'
$gameDir = Get-GameDir $game.CommandLine
if ($gameDir) {
    Write-Host "  game dir: $gameDir" -ForegroundColor DarkGray
    Copy-Tail (Join-Path $gameDir 'logs\latest.log') (Join-Path $OutDir 'latest.log') | Out-Null
    Copy-Tail (Join-Path $gameDir 'logs\debug.log')  (Join-Path $OutDir 'debug.log')  | Out-Null

    Get-ChildItem -Path (Join-Path $gameDir 'crash-reports') -Filter '*.txt' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 2 |
        Copy-Item -Destination $OutDir

    Get-ChildItem -Path (Join-Path $gameDir 'mods') -File -ErrorAction SilentlyContinue |
        Select-Object Name, @{ n = 'SizeKB'; e = { [math]::Round($_.Length / 1KB) } }, LastWriteTime |
        Format-Table -AutoSize | Out-String -Width 200 |
        Set-Content -Path (Join-Path $OutDir 'mods.txt') -Encoding UTF8
} else {
    Write-Warn 'Could not read --gameDir from the command line; game logs skipped.'
}

foreach ($launcher in Get-LauncherDirs) {
    Get-ChildItem -Path (Join-Path $launcher 'logs') -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 2 |
        ForEach-Object { Copy-Item $_.FullName -Destination (Join-Path $OutDir "launcher-$($_.Name)") }

    Get-ChildItem -Path (Join-Path $launcher 'clusters') -Filter 'cluster-output.log' -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 3 |
        ForEach-Object { Copy-Item $_.FullName -Destination (Join-Path $OutDir "output-$($_.Directory.Name).log") }
}

$game.CommandLine | Set-Content -Path (Join-Path $OutDir 'commandline.txt') -Encoding UTF8
@(
    "pid          : $gamePid"
    "executable   : $($game.ExecutablePath)"
    "started      : $($game.CreationDate)"
    "game dir     : $gameDir"
    "jcmd         : $jcmd"
    "os           : $((Get-CimInstance Win32_OperatingSystem).Caption) $([Environment]::OSVersion.Version)"
) | Set-Content -Path (Join-Path $OutDir 'summary.txt') -Encoding UTF8

$firstDump = Join-Path $OutDir 'threaddump-1.txt'
if (Test-Path $firstDump) {
    Write-Step 'Main thread, first dump'
    $lines = Get-Content $firstDump
    $start = ($lines | Select-String -Pattern '^"main"' | Select-Object -First 1).LineNumber
    if ($start) {
        $lines[($start - 1)..([math]::Min($start + 28, $lines.Count - 1))] |
            ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
        Write-Host ''
        Write-Host '  Same stack in every dump = that is the freeze.' -ForegroundColor Green
    } else {
        Write-Warn 'No "main" thread in the dump - the hang may be before the JVM got that far.'
    }
}

$zip = "$OutDir.zip"
Compress-Archive -Path (Join-Path $OutDir '*') -DestinationPath $zip -Force
Write-Host ''
Write-Step "Done: $zip"
Write-Host '  Send that zip to the PolyPlus developers.'
