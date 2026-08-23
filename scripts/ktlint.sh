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
# The version is pinned to match what the build actually enforces, and that pin is load-bearing.
# ktlint's standard ruleset moves in both directions between releases: newer versions add rules this
# repository has never had to satisfy, and they also *relax* ones it does. The pin was 1.7.2, chosen by
# comparing findings against CI runs, and that turned out to be a version newer than the one the
# ktlint Gradle plugin bundles - so it agreed on everything the comparison happened to cover and
# silently disagreed elsewhere. It passed a file with an unused import that the Gradle task then
# failed, which cost exactly the round this script exists to save.
#
# So the pin is now derived from the plugin rather than from sampling: it is the ktlint the plugin
# resolves. Check it with
#
#   find ~/.gradle/caches -name 'ktlint-rule-engine-*.jar'
#
# after any `ktlintGradle` bump in gradle/libs.versions.toml, and move this line to match. Guessing a
# newer version does not make the check stricter; it makes it answer a different question.
#
#   scripts/ktlint.sh            check, exit non-zero on violations
#   scripts/ktlint.sh --format   fix what can be fixed automatically
set -euo pipefail

KTLINT_VERSION="1.5.0"
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
