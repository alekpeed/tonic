# Tonic

An Android ear-training app that builds functional aural skill — hearing a
note in context and knowing what it's doing, not just naming an interval —
from absolute zero. See `docs/00-README.md` for the full spec set and
`CLAUDE.md` for the project constitution.

## Building

Requires JDK 17+ and the Android SDK (`ANDROID_HOME` or `local.properties`
pointing at it — platforms 35/36 and matching build-tools).

One command that builds, lints (ktlint), and runs every JVM-level test —
the fast, no-emulator subset covering `:core:model`, `:core:curriculum`,
and `:core:engine` plus every Android module's Robolectric/unit tests:

```
./gradlew build
```

**When the question is "is the tree green," run the gate rather than gradle:**

```bash
./scripts/verify.sh
```

It runs the same build and then re-derives both golden baselines under
`--rerun-tasks`, and — the reason it exists — it reads Gradle's own exit code
instead of a pipeline's. Two Phase 2 stages were reported green off a command
like `./gradlew build -q | grep -v ... | tail`, whose exit status is `tail`'s and
therefore always zero; two real failures sat hidden across two commits. Never put
a pipe between yourself and Gradle's status.


To run just the pure-Kotlin engine/curriculum/model test suites:

```
./gradlew jvmTestAll
```

To auto-fix formatting instead of just checking it:

```
./gradlew ktlintFormat
```

Both of those need the Android SDK. To check style without it — useful in a
sandbox that has no SDK, and much faster than waiting for CI:

```
scripts/ktlint.sh            # check
scripts/ktlint.sh --format   # fix what can be fixed
```

It downloads a standalone ktlint once, pinned to the version CI enforces, and
reads the project's own `.editorconfig`.

## Toolchain notes (as of this build)

The Android/Kotlin ecosystem moved considerably between this spec being
written and this build running (AGP 9, which bundles Kotlin support
directly and drops the standalone `org.jetbrains.kotlin.android` plugin,
plus AAR metadata on the newest androidx releases requiring `compileSdk 37`).
Two decisions worth knowing about if you touch the build:

- `gradle.properties` sets `android.builtInKotlin=false` and
  `android.newDsl=false` to keep the classic Kotlin-plugin-per-module model,
  because this project relies on **kapt** (Room + Hilt annotation
  processing) and AGP 9's built-in Kotlin support does not allow kapt at
  all. Migrating to KSP and built-in Kotlin is possible later but is a
  deliberate, separate change — not something to do implicitly.
- Androidx library versions in `gradle/libs.versions.toml` are pinned to
  the newest release *of each artifact* that still declares
  `minCompileSdk <= 35`, matching this project's fixed `compileSdk`/
  `targetSdk 35` (`CLAUDE.md` §1). The literal latest releases of
  `compose-bom`, `activity-compose`, and `androidx.hilt:hilt-navigation-compose`
  as of this build require `compileSdk 37`, whose platform isn't resolvable
  through the cmdline-tools version available in the build environment —
  hence the deliberately-older pins rather than bumping `compileSdk`.

## Continuous verification

`.github/workflows/verify.yml` runs `scripts/verify.sh` on every push and pull
request, on a GitHub-hosted runner with the Android SDK installed. That is the
authoritative green/red signal: the full build, ktlint, every JVM and
Robolectric test, then both golden corpora re-derived under `--rerun-tasks` so
a generator change cannot pass by never having been run. The complete build log
is uploaded as an artifact on every run, pass or fail.

## Known verification gap: no device or emulator

The development sandboxes for this project have no physical Android device, no
emulator (`/dev/kvm` is unavailable, so the AVD can't boot), and no display.
Everything that can be verified without one is verified in CI — JVM unit tests,
property-based tests, simulation tests, Robolectric-backed Android-dependent
tests, ktlint, `assembleDebug`/`assembleRelease` (R8) builds.

What still requires a human with hardware:

- **Stage 1.2's audio listening pass — done.** Signed off 2026-08-21, along with
  a listening pass over Phase 2's audio. See `docs/09-BUILD-PLAN.md`.
- **Still outstanding:** real-device performance and frame-drop checks, TalkBack
  navigation, physical-interruption handling, and the task-viability questions in
  `docs/21-HANDOFF.md` §4 — whether a learner can actually *perform* Phase 2's
  exercises, which hearing them does not establish.
- **Phase 3 raises this sharply.** Microphone input cannot be tested on the JVM
  at all, so `docs/30-PHASE-3-SPEC.md` Stage 3.0 shifts the verification burden
  onto a device for the whole phase, not just at its gate.
