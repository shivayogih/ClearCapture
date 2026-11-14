#!/usr/bin/env sh
# Prepares this checkout for building on macOS or Linux. Safe to run any number of times.
#  1. Finds the Android SDK: ANDROID_HOME, then ANDROID_SDK_ROOT, then the default install path.
#  2. Writes sdk.dir into local.properties (machine-specific, git-ignored), keeping any other keys.
#  3. Checks the SDK packages the build needs; with --install, installs missing ones with sdkmanager.
#  4. Reports the JDK. Gradle runs on JDK 21 and downloads it if missing (gradle/gradle-daemon-jvm.properties).
set -eu
repo=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
install=0
[ "${1:-}" = "--install" ] && install=1

case "$(uname -s)" in
  Darwin) default_sdk="$HOME/Library/Android/sdk" ;;
  *) default_sdk="$HOME/Android/Sdk" ;;
esac
sdk=""
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$default_sdk"; do
  if [ -n "$candidate" ] && { [ -d "$candidate/platform-tools" ] || [ -d "$candidate/platforms" ]; }; then
    sdk="$candidate"
    break
  fi
done
if [ -z "$sdk" ]; then
  echo "Android SDK not found. Install Android Studio or the command-line tools, set ANDROID_HOME and run again." >&2
  exit 1
fi
echo "Android SDK: $sdk"

props="$repo/local.properties"
tmp="$props.tmp"
echo "sdk.dir=$sdk" > "$tmp"
[ -f "$props" ] && grep -v '^[[:space:]]*sdk\.dir[[:space:]]*=' "$props" >> "$tmp" || true
mv "$tmp" "$props"
echo "Wrote $props"

compile_sdk=$(sed -n 's/^compileSdk *= *"\([0-9]*\)".*/\1/p' "$repo/gradle/libs.versions.toml")
missing=""
ls -d "$sdk/platforms/android-$compile_sdk"* >/dev/null 2>&1 || missing="$missing platforms;android-$compile_sdk"
[ -d "$sdk/build-tools/36.0.0" ] || missing="$missing build-tools;36.0.0"
[ -d "$sdk/platform-tools" ] || missing="$missing platform-tools"
if [ -z "$missing" ]; then
  echo "SDK packages: all present"
elif [ "$install" -eq 1 ]; then
  sdkmanager=$(find "$sdk/cmdline-tools" -name sdkmanager -type f 2>/dev/null | head -n 1)
  [ -n "$sdkmanager" ] || { echo "sdkmanager not found: install Android SDK Command-line Tools." >&2; exit 1; }
  # shellcheck disable=SC2086
  "$sdkmanager" $missing
else
  echo "Missing SDK packages:$missing. Run again with --install, or use Android Studio's SDK Manager." >&2
fi

if command -v java >/dev/null 2>&1; then java_version=$(java -version 2>&1 | head -n 1); else java_version="none on PATH"; fi
echo "java on PATH: $java_version (Gradle runs on JDK 21 regardless and downloads it when it is not installed)"
echo "Ready. Build with: ./gradlew check assembleDebug"
