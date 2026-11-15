<#
.SYNOPSIS
  Prepares this checkout for building on Windows. Safe to run any number of times.

.DESCRIPTION
  1. Finds the Android SDK: ANDROID_HOME, then ANDROID_SDK_ROOT, then %LOCALAPPDATA%\Android\Sdk.
  2. Writes sdk.dir into local.properties (machine-specific, git-ignored), keeping any other keys.
  3. Checks the SDK packages the build needs and installs missing ones with sdkmanager (-Install).
  4. Reports the JDK. Gradle itself runs on JDK 21 and downloads it if missing (gradle/gradle-daemon-jvm.properties).

.EXAMPLE
  ./scripts/bootstrap.ps1            # check and write local.properties
  ./scripts/bootstrap.ps1 -Install   # also install missing SDK packages
#>
param([switch]$Install)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot

$candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) | Where-Object { $_ }
$sdk = $candidates | Where-Object { (Test-Path (Join-Path $_ 'platform-tools')) -or (Test-Path (Join-Path $_ 'platforms')) } | Select-Object -First 1
if (-not $sdk) {
    Write-Error "Android SDK not found. Install Android Studio or the command-line tools, then set ANDROID_HOME to the SDK folder and run this again."
}
Write-Host "Android SDK: $sdk"

# local.properties: escape ':' and use '/' as Gradle's properties format requires.
$props = Join-Path $repo 'local.properties'
$escaped = ($sdk -replace '\\', '/') -replace ':', '\:'
$lines = @()
if (Test-Path $props) { $lines = Get-Content $props | Where-Object { $_ -notmatch '^\s*sdk\.dir\s*=' } }
$lines = @("sdk.dir=$escaped") + $lines
[IO.File]::WriteAllText($props, (($lines -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
Write-Host "Wrote $props"

# Required packages, read from the version catalog so nothing is hard-coded twice.
$catalog = Get-Content (Join-Path $repo 'gradle\libs.versions.toml') -Raw
$compileSdk = [regex]::Match($catalog, '(?m)^compileSdk\s*=\s*"(\d+)"').Groups[1].Value
$required = [ordered]@{
    "platforms;android-$compileSdk" = { Get-ChildItem (Join-Path $sdk 'platforms') -Directory -Filter "android-$compileSdk*" -ErrorAction SilentlyContinue }
    'build-tools;36.0.0'             = { Test-Path (Join-Path $sdk 'build-tools\36.0.0') }
    'platform-tools'                 = { Test-Path (Join-Path $sdk 'platform-tools') }
}
$missing = @($required.Keys | Where-Object { -not (& $required[$_]) })
if ($missing.Count -eq 0) {
    Write-Host 'SDK packages: all present'
} elseif ($Install) {
    $sdkmanager = Get-ChildItem (Join-Path $sdk 'cmdline-tools') -Recurse -Filter 'sdkmanager.bat' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $sdkmanager) { Write-Error 'sdkmanager not found: install "Android SDK Command-line Tools" from the SDK Manager.' }
    & $sdkmanager.FullName @missing
} else {
    Write-Warning ("Missing SDK packages: " + ($missing -join ', ') + ". Run again with -Install, or install them in Android Studio's SDK Manager.")
}

$java = Get-Command java -ErrorAction SilentlyContinue
$javaVersion = if ($java) { (& java -version 2>&1 | Select-Object -First 1) } else { 'none on PATH' }
Write-Host "java on PATH: $javaVersion (Gradle runs on JDK 21 regardless and downloads it when it is not installed)"
Write-Host 'Ready. Build with: ./gradlew.bat check assembleDebug'
