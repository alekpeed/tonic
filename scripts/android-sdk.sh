#!/usr/bin/env bash
#
# Install the Android SDK this project builds against, so scripts/verify.sh can run locally.
#
# Why this exists. This project was developed on the assumption that its sandboxes have no Android
# SDK and therefore cannot compile at all — docs/21-HANDOFF.md §2 states it outright ("CI is the
# compiler"), and most of that file's §6 and §7 are consequences of it: five to eleven minutes per
# round, one failure category at a time, scripts/symcheck.py written to approximate a compiler, and a
# run burned on a member access no local check could see. On 2026-08-22 the assumption was tested
# rather than inherited, and it was wrong: the SDK downloads and installs fine, and
# `scripts/verify.sh` completes green in the sandbox.
#
# So this script exists to make that reproducible rather than something one session happened to do.
# It is not a replacement for CI. CI remains the authoritative signal — it runs on a known-clean
# machine and checks things this cannot (the committed Room schemas, the APK signature). What a local
# run buys is the difference between finding a compile error in ten seconds and finding it in ten
# minutes.
#
# What it installs, and why exactly these versions: the same two packages
# .github/workflows/verify.yml installs, because a local build that used different ones would be
# answering a different question than the gate. The pin is load-bearing for the same reason
# scripts/ktlint.sh's is.
#
# Roughly 1.5 GB on disk and a few minutes on a cold run. Idempotent: re-running with everything
# already present does nothing but re-check.
#
#   scripts/android-sdk.sh          install (or verify an existing install), then point the build at it
#
# Override the location with TONIC_ANDROID_SDK. It deliberately defaults *outside* the repository:
# build/ is where a cache would naturally go and is also the first thing a `clean` removes.
set -euo pipefail

# Must match .github/workflows/verify.yml's "Install the pinned Android platform" step, which in turn
# follows compileSdk 35 (CLAUDE.md §1). Change these three together or not at all.
CMDLINE_TOOLS_BUILD="11076708"
PLATFORM_PACKAGE="platforms;android-35"
BUILD_TOOLS_PACKAGE="build-tools;35.0.0"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${TONIC_ANDROID_SDK:-$HOME/android-sdk}"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

if ! command -v java > /dev/null 2>&1; then
  echo "error: java is not on PATH. sdkmanager and Gradle both need a JDK (17+; CI uses 21)." >&2
  exit 1
fi

# --- 1. cmdline-tools, which is what installs everything else -----------------------------------

if [ ! -x "$SDKMANAGER" ]; then
  echo "android-sdk: fetching command-line tools $CMDLINE_TOOLS_BUILD"
  URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
  TMP="$(mktemp -d)"
  # A partial download unzips into a broken tools directory that then looks installed on every later
  # run, so land it through a temp directory rather than in place. Same reasoning as ktlint.sh's .part.
  trap 'rm -rf "$TMP"' EXIT
  curl -sSL --fail --max-time 900 -o "$TMP/tools.zip" "$URL"
  unzip -q "$TMP/tools.zip" -d "$TMP"
  mkdir -p "$SDK_DIR/cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  # The zip unpacks to a directory literally named cmdline-tools; sdkmanager requires it to sit at
  # <sdk>/cmdline-tools/<channel>/ and infers the SDK root from that position.
  mv "$TMP/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$TMP"
  trap - EXIT
else
  echo "android-sdk: command-line tools already present"
fi

# --- 2. licenses and packages --------------------------------------------------------------------

platform_dir="$SDK_DIR/${PLATFORM_PACKAGE//;//}"
build_tools_dir="$SDK_DIR/${BUILD_TOOLS_PACKAGE//;//}"

if [ -d "$platform_dir" ] && [ -d "$build_tools_dir" ]; then
  echo "android-sdk: $PLATFORM_PACKAGE and $BUILD_TOOLS_PACKAGE already installed"
else
  # Bounded input rather than `yes |`. Under `set -o pipefail`, `yes` takes SIGPIPE the moment
  # sdkmanager stops reading and fails the whole pipeline on what is actually a success.
  echo "android-sdk: accepting licenses"
  printf 'y\n%.0s' $(seq 1 100) | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses > /dev/null

  echo "android-sdk: installing $PLATFORM_PACKAGE $BUILD_TOOLS_PACKAGE (about 1.5 GB)"
  "$SDKMANAGER" --sdk_root="$SDK_DIR" "$PLATFORM_PACKAGE" "$BUILD_TOOLS_PACKAGE" > /dev/null
fi

# --- 3. check what was installed, rather than that a command exited 0 ----------------------------
#
# docs/21-HANDOFF.md §7, on a scripted edit that matched nothing and was pushed anyway: assert the
# intended result is present afterward. An sdkmanager that succeeds having installed nothing is
# exactly that failure wearing a zero exit code.

missing=""
[ -f "$platform_dir/android.jar" ] || missing="$missing $platform_dir/android.jar"
[ -x "$build_tools_dir/apksigner" ] || missing="$missing $build_tools_dir/apksigner"
if [ -n "$missing" ]; then
  echo "error: the install reported success but these are missing:$missing" >&2
  exit 1
fi

# --- 4. point the build at it ---------------------------------------------------------------------
#
# local.properties is gitignored and is how AGP finds the SDK without an environment variable, which
# means `./gradlew` and `scripts/verify.sh` work in a fresh shell rather than only in the one that
# exported ANDROID_HOME.

LOCAL_PROPERTIES="$ROOT/local.properties"
if grep -qs '^sdk\.dir=' "$LOCAL_PROPERTIES"; then
  existing="$(sed -n 's/^sdk\.dir=//p' "$LOCAL_PROPERTIES" | head -1)"
  if [ "$existing" = "$SDK_DIR" ]; then
    echo "android-sdk: local.properties already points here"
  else
    # Never silently repoint someone else's SDK. Report and let them decide.
    echo "android-sdk: local.properties already sets sdk.dir=$existing — left as is."
    echo "             To use this install instead, set it to $SDK_DIR."
  fi
else
  printf 'sdk.dir=%s\n' "$SDK_DIR" >> "$LOCAL_PROPERTIES"
  echo "android-sdk: wrote sdk.dir=$SDK_DIR to local.properties (gitignored)"
fi

echo
echo "android-sdk: ready. The gate now runs here:"
echo
echo "    scripts/verify.sh"
echo
echo "It is still not the whole gate — no device, no emulator (see README, \"Known verification gap\"),"
echo "and CI remains the authoritative signal. Push and read the run before reporting green."
