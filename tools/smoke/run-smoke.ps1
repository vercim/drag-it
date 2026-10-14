<#
.SYNOPSIS
  Runs the scripted gesture test (DragItSmoke) on the dev client of each target and prints one verdict per target.

.DESCRIPTION
  For every target it: switches the Stonecutter active project, copies the harness from tools/smoke into src/,
  starts `runClient`, waits for the harness to finish (it ends the game itself), reads run/smoke.log and
  run/logs/latest.log, and removes the harness again. The harness never stays in src/ and never reaches a JAR.

  Close every running Minecraft dev client and Gradle run first: a running one holds the Gradle lock.

.PARAMETER Targets
  Targets to test, for example 1.21.4-neoforge 26.3-fabric. Default: every folder in versions/.

.PARAMETER Install
  Only copy the harness into src/ (for a run from the IDE) and exit.

.PARAMETER Uninstall
  Only remove the harness from src/ (also after a crashed run) and exit.

.PARAMETER TimeoutMinutes
  Per-target limit; a client still alive after it is killed and the target is reported as TIMEOUT.
#>
param(
    [string[]]$Targets,
    [switch]$Install,
    [switch]$Uninstall,
    [int]$TimeoutMinutes = 10
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $root

# pack_format (the major number the harness writes into its test packs) per Minecraft version.
# A new version must be added here; the script refuses to test a target it has no format for.
$packFormats = @{
    '1.21.1'  = 34
    '1.21.4'  = 46
    '1.21.10' = 69
    '1.21.11' = 75
    '26.1'    = 84
    '26.1.2'  = 84
    '26.2'    = 88
    '26.3'    = 97
}

$javaDir = Join-Path $root 'src\main\java\dev\vercim\dragit'
$mixinsJson = Join-Path $root 'src\main\resources\dragit.mixins.json'
$harness = @(
    @{ File = 'DragItSmoke.java';          Dest = (Join-Path $javaDir 'drag') },
    @{ File = 'HeldTestMixin.java';        Dest = (Join-Path $javaDir 'mixin') },
    @{ File = 'MouseBlockTestMixin.java';  Dest = (Join-Path $javaDir 'mixin') },
    @{ File = 'SmokeStartTestMixin.java';  Dest = (Join-Path $javaDir 'mixin') }
)
$testMixins = @('HeldTestMixin', 'MouseBlockTestMixin', 'SmokeStartTestMixin')
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Remove-Harness {
    foreach ($h in $harness) {
        Remove-Item (Join-Path $h.Dest $h.File) -Force -ErrorAction SilentlyContinue
    }
    $json = [System.IO.File]::ReadAllText($mixinsJson)
    $clean = [regex]::Replace($json, '(?m)^[ \t]*"(HeldTestMixin|MouseBlockTestMixin|SmokeStartTestMixin)",\r?\n', '')
    if ($clean -ne $json) { [System.IO.File]::WriteAllText($mixinsJson, $clean, $utf8) }
}

function Install-Harness {
    Remove-Harness
    foreach ($h in $harness) {
        Copy-Item (Join-Path $PSScriptRoot $h.File) $h.Dest -Force
    }
    $json = [System.IO.File]::ReadAllText($mixinsJson)
    $nl = if ($json.Contains("`r`n")) { "`r`n" } else { "`n" }
    $entries = ($testMixins | ForEach-Object { "    `"$_`"," }) -join $nl
    $patched = $json.Replace('"MouseHandlerMixin",', '"MouseHandlerMixin",' + $nl + $entries)
    if ($patched -eq $json) { throw 'Could not register the test mixins: "MouseHandlerMixin" not found in dragit.mixins.json' }
    [System.IO.File]::WriteAllText($mixinsJson, $patched, $utf8)
}

# Runs gradlew.bat and waits; stdout and stderr go to files (calling native tools with a redirect trips over stderr in Windows PowerShell 5.1).
function Invoke-Gradle([string]$ArgLine, [string]$LogBase, [int]$TimeoutMs = -1) {
    $p = Start-Process -FilePath (Join-Path $root 'gradlew.bat') -ArgumentList $ArgLine -NoNewWindow -PassThru `
        -RedirectStandardOutput "$LogBase.log" -RedirectStandardError "$LogBase.err.log"
    if ($p.WaitForExit($TimeoutMs)) { return [pscustomobject]@{ TimedOut = $false; ExitCode = $p.ExitCode } }
    & taskkill /PID $p.Id /T /F | Out-Null
    return [pscustomobject]@{ TimedOut = $true; ExitCode = -1 }
}

if ($Uninstall) { Remove-Harness; Write-Host 'Harness removed from src/.'; return }
if ($Install)   { Install-Harness; Write-Host 'Harness copied into src/. Run tools\smoke\run-smoke.ps1 -Uninstall when done.'; return }

if (-not $Targets) {
    $Targets = Get-ChildItem (Join-Path $root 'versions') -Directory | Sort-Object Name | ForEach-Object { $_.Name }
}
foreach ($t in $Targets) {
    if (-not (Test-Path (Join-Path $root "versions\$t"))) { throw "Unknown target '$t' (no versions\$t)." }
    $mc = $t -replace '-(fabric|neoforge)$', ''
    if (-not $packFormats.ContainsKey($mc)) { throw "No pack_format for Minecraft ${mc}: add it to `$packFormats in tools/smoke/run-smoke.ps1." }
}

$outDir = Join-Path $root 'build\smoke'
New-Item -ItemType Directory -Force $outDir | Out-Null
$results = @()

try {
    foreach ($t in $Targets) {
        $mc = $t -replace '-(fabric|neoforge)$', ''
        Write-Host "=== $t ===" -ForegroundColor Cyan
        $run = Join-Path $root "versions\$t\run"
        $status = 'ERROR'; $pass = 0; $fail = 0; $note = ''

        try {
            $sw = Invoke-Gradle "`"Set active project to $t`" --console=plain" (Join-Path $outDir "$t.switch")
            if ($sw.TimedOut -or $sw.ExitCode -ne 0) { throw "Set active project failed (see build\smoke\$t.switch.log)" }
            Install-Harness

            New-Item -ItemType Directory -Force $run | Out-Null
            Remove-Item (Join-Path $run 'smoke.log'), (Join-Path $run 'screenshots'), (Join-Path $run 'logs\latest.log') -Recurse -Force -ErrorAction SilentlyContinue
            Set-Content -Path (Join-Path $run 'smoke.format') -Value $packFormats[$mc]
            # a fresh profile opens the accessibility onboarding instead of the title screen; keep the rest of options.txt
            $opts = Join-Path $run 'options.txt'
            $lines = @()
            if (Test-Path $opts) { $lines = @(Get-Content $opts | Where-Object { $_ -notmatch '^onboardAccessibility:' }) }
            Set-Content -Path $opts -Value ($lines + 'onboardAccessibility:false')

            $gr = Invoke-Gradle ":${t}:runClient --console=plain" (Join-Path $outDir "$t.gradle") ($TimeoutMinutes * 60 * 1000)
            if ($gr.TimedOut) {
                $status = 'TIMEOUT'
                # the client is a child of the Gradle daemon, not of the wrapper: find it by its target folder
                Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
                    Where-Object { $_.CommandLine -match [regex]::Escape($t) -and $_.CommandLine -match 'minecraft|neoforge|fabric' } |
                    ForEach-Object { Write-Host "killing leftover client $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force }
            }

            $smoke = Join-Path $run 'smoke.log'
            if (-not (Test-Path $smoke)) {
                if ($status -ne 'TIMEOUT') { $status = 'NO-LOG'; $note = 'the harness wrote no smoke.log (see build\smoke\' + $t + '.gradle.log)' }
            } else {
                $log = Get-Content $smoke
                $pass = @($log | Where-Object { $_ -match '^PASS ' }).Count
                $fail = @($log | Where-Object { $_ -match '^FAIL' }).Count   # FAIL <check> and FAILED <exception>
                $done = @($log | Where-Object { $_ -eq 'DONE' }).Count -gt 0
                $latest = Join-Path $run 'logs\latest.log'
                $mixinErrors = 0
                if (Test-Path $latest) {
                    $mixinErrors = @(Select-String -Path $latest -Pattern 'Drag & drop failed|Mixin apply|InvalidInjection|InvalidAccessor|MixinApplyError|Mixin transformation|Critical injection failure').Count
                }
                if ($status -eq 'TIMEOUT') { $note = 'client did not finish in time' }
                elseif (-not $done)        { $status = 'INCOMPLETE'; $note = 'no DONE line' }
                elseif ($fail -gt 0)       { $status = 'FAIL'; $note = 'see versions\' + $t + '\run\smoke.log' }
                elseif ($mixinErrors -gt 0){ $status = 'MIXIN-ERROR'; $note = "$mixinErrors error line(s) in latest.log" }
                elseif ($pass -eq 0)       { $status = 'NO-CHECKS'; $note = 'DONE but no PASS line' }
                else                       { $status = 'OK' }
            }
        } catch {
            $status = 'ERROR'; $note = $_.Exception.Message
        }

        $color = if ($status -eq 'OK') { 'Green' } else { 'Red' }
        Write-Host ("{0}: {1} (PASS {2}, FAIL {3}) {4}" -f $t, $status, $pass, $fail, $note) -ForegroundColor $color
        $results += [pscustomobject]@{ Target = $t; Status = $status; Pass = $pass; Fail = $fail; Note = $note }
    }
} finally {
    Remove-Harness
    Invoke-Gradle '"Reset active project" --console=plain' (Join-Path $outDir 'reset') | Out-Null
}

Write-Host ''
Write-Host '=== Summary ===' -ForegroundColor Cyan
$summary = $results | Format-Table -AutoSize | Out-String
Write-Host $summary
Set-Content -Path (Join-Path $outDir 'summary.txt') -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm') + "`n" + $summary)

$notOk = @($results | Where-Object { $_.Status -ne 'OK' })
if ($notOk.Count -gt 0) {
    Write-Host "$($notOk.Count) of $($results.Count) targets are NOT ok." -ForegroundColor Red
    exit 1
}
Write-Host "All $($results.Count) targets ok." -ForegroundColor Green
