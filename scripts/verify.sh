#!/usr/bin/env bash
#
# The verification gate. Run this instead of calling ./gradlew directly when the
# question is "is the tree green."
#
# It exists because of a real reporting failure. Stages 2.5 and 2.6 of the Phase 2
# build were both reported as "full build green" and neither was: the command used
# was
#
#     ./gradlew build -q 2>&1 | grep -v ... | tail -40
#
# and the exit code of a pipeline is the exit code of its *last* command. `tail`
# always succeeds. Gradle's failure was invisible, and two real defects — a mastery
# criterion added without updating the guard that pins the criterion set, and a test
# file that stopped compiling after a signature change — sat broken across two
# commits while the reports said otherwise.
#
# So: no pipes around gradle here. Output goes to a file, the exit code is read
# directly from gradle, and this script's own exit code is that code.

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

LOG="${TONIC_VERIFY_LOG:-$ROOT/build/verify.log}"
mkdir -p "$(dirname "$LOG")"

echo "verify: ./gradlew build --max-workers=2  (log: $LOG)"

# No pipe. The redirect keeps the log without putting another process between us
# and gradle's status.
./gradlew build --max-workers=2 > "$LOG" 2>&1
STATUS=$?

# What a failure actually says, rather than where the log happened to end.
#
# A tail is the wrong tool for a compile error. Kotlin prints its diagnostics as `e:` lines and then
# gradle appends its own summary, the deprecation warnings, the problems-report path and the task
# count on top - so a 40-line tail can end with "Compilation error. See log for more details" and none
# of the details. That is exactly what happened on run #27: the build failed, and the report said only
# that it had. Diagnostics are pulled out by prefix first, and the tail follows for everything else.
report_failure() {
  echo
  echo "verify: FAILED ($1, gradle exit $2)"

  if grep -qE "^e: " "$LOG" 2>/dev/null; then
    echo
    echo "verify: compiler errors"
    grep -E "^e: " "$LOG" | head -40 | sed "s/^/  /"
  fi

  # Test failures are the other case a tail truncates: gradle prints each one where it happens and
  # then summarizes far below.
  if grep -qE "^[A-Za-z0-9_.]+ > .* FAILED$" "$LOG" 2>/dev/null; then
    echo
    echo "verify: failing tests"
    grep -E "^[A-Za-z0-9_.]+ > .* FAILED$" "$LOG" | head -40 | sed "s/^/  /"
  fi

  echo
  echo "verify: last 40 lines of $LOG"
  echo
  tail -40 "$LOG"
}

if [ "$STATUS" -ne 0 ]; then
  report_failure "build" "$STATUS"
  exit "$STATUS"
fi

# A green build is necessary but not sufficient: the golden baselines must also be
# re-derived rather than replayed from the build cache, or a generator change can
# pass by never having been run. --rerun-tasks is what makes that real.
echo "verify: golden baselines under --rerun-tasks"
./gradlew :core:curriculum:test --tests '*M2GoldenCorpusTest*' \
          :feature:practice:testDebugUnitTest --tests '*M2SessionTraceTest*' \
          --rerun-tasks --max-workers=2 >> "$LOG" 2>&1
GOLDEN=$?

if [ "$GOLDEN" -ne 0 ]; then
  report_failure "golden baselines" "$GOLDEN"
  exit "$GOLDEN"
fi

echo "verify: OK — build green, both golden baselines byte-identical"

# Surface the measurements. docs/10-TESTING.md §5 asks for simulation and measurement output to be
# treated as a report rather than a pass/fail, and until now it was neither read nor readable: the
# gradle redirect above sends all test output into $LOG, so even with testLogging enabled the numbers
# only ever reached the uploaded artifact, which someone has to go and download. Tests mark the lines
# worth surfacing with a [measure] prefix; full detail stays in the log.
#
# This runs after gradle has exited and its status is already captured, so it cannot affect the exit
# code - which is the one property this script exists to protect.
if grep -qF "[measure]" "$LOG" 2>/dev/null; then
  echo
  echo "verify: measurements"
  grep -F "[measure]" "$LOG" | sed "s/^[[:space:]]*/  /"
fi
