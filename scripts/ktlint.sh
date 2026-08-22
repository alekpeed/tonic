#!/usr/bin/env bash
#
# Run ktlint locally, without Gradle and without the Android SDK.
#
# Why this exists. The development sandboxes for this project have no Android SDK, so `./gradlew
# ktlintCheck` cannot run and style violations were only discovered by pushing and waiting for CI —
# roughly five minutes per round, one rule at a time. Four consecutive CI failures were spent that way
# on formatting alone. The ktlint CLI has no such dependency: it is a standalone jar that reads the
# project's own .editorconfig, so the same rules can be checked here in seconds.
#
# The version is pinned to match what CI actually enforces, and that pin is load-bearing. ktlint's
# standard ruleset grows between releases: 1.8.0 reports violations across files this repository has
# always passed, because it added rules the version in CI does not have. A newer version would send
# someone chasing failures that do not exist, which is worse than not having this script. 1.7.2 was
# verified to match by running it against a source set CI had just passed (clean) and one CI had just
# failed (identical findings).
#
# If CI ever starts disagreeing with this script, the plugin's bundled ktlint version has moved.
# Re-derive the pin the same way rather than guessing.
#
#   scripts/ktlint.sh            check, exit non-zero on violations
#   scripts/ktlint.sh --format   fix what can be fixed automatically
set -euo pipefail

KTLINT_VERSION="1.7.2"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${TONIC_KTLINT_CACHE:-$ROOT/build/tools}"
JAR="$CACHE/ktlint-cli-$KTLINT_VERSION-all.jar"

if [ ! -f "$JAR" ]; then
  mkdir -p "$CACHE"
  URL="https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/$KTLINT_VERSION/ktlint-cli-$KTLINT_VERSION-all.jar"
  echo "ktlint: fetching $KTLINT_VERSION (about 70 MB, once)"
  # A partial download would look like a corrupt jar on every later run, so land it atomically.
  curl -sSL --fail --max-time 300 -o "$JAR.part" "$URL"
  mv "$JAR.part" "$JAR"
fi

cd "$ROOT"
exec java -jar "$JAR" --relative "$@" "**/src/**/*.kt" "**/*.kts"
