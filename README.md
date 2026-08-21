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

## Known verification gap: no device or emulator

This project was built in a sandboxed environment with no physical Android
device, no emulator (`/dev/kvm` is unavailable, so the AVD can't boot), and
no display. Everything that can be verified without one has been — JVM unit
tests, property-based tests, simulation tests, Robolectric-backed
Android-dependent tests, ktlint, `assembleDebug`/`assembleRelease` (R8)
builds. Anything the spec calls a **manual, on-device** gate (the Stage 1.2
audio listening pass, real-device performance/frame-drop checks, TalkBack
navigation, physical-interruption handling) has *not* been verified and is
called out explicitly wherever it applies. Run those before trusting this
build fully.
