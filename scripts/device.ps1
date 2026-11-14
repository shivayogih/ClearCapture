<#
.SYNOPSIS
  Small helpers to drive the POC on a connected device or emulator, for repeatable manual and smoke checks.

.EXAMPLE
  . ./scripts/device.ps1          # dot-source to load the functions
  Show-Screen                     # list visible texts and content descriptions with their bounds
  Tap-Text 'Capture: container'   # tap the first element whose text or description matches
  Save-Screenshot docs/screenshots/camera.png
#>
$script:Adb = Join-Path ($env:ANDROID_HOME ?? (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) 'platform-tools\adb.exe'
$script:Dump = Join-Path ([IO.Path]::GetTempPath()) 'cc-ui.xml'

function Get-Nodes {
    & $script:Adb shell uiautomator dump /sdcard/cc-ui.xml | Out-Null
    & $script:Adb pull /sdcard/cc-ui.xml $script:Dump | Out-Null
    ([xml](Get-Content $script:Dump)).SelectNodes('//node')
}

function Show-Screen {
    Get-Nodes | Where-Object { $_.text -or $_.'content-desc' } |
        ForEach-Object { '{0} [{1}] {2}' -f $_.text, $_.'content-desc', $_.bounds }
}

function Tap-Text([string]$Pattern) {
    $node = Get-Nodes | Where-Object { $_.text -like "*$Pattern*" -or $_.'content-desc' -like "*$Pattern*" } | Select-Object -First 1
    if (-not $node) { throw "Nothing on screen matches '$Pattern'" }
    $n = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $script:Adb shell input tap ([int](($n[0] + $n[2]) / 2)) ([int](($n[1] + $n[3]) / 2))
}

function Save-Screenshot([string]$Path) {
    New-Item -ItemType Directory -Force (Split-Path $Path) | Out-Null
    & $script:Adb shell screencap -p /sdcard/cc-shot.png
    & $script:Adb pull /sdcard/cc-shot.png $Path | Out-Null
    Get-Item $Path
}
