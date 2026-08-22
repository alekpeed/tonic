package com.tonic.feature.practice.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sets `Dispatchers.Main` for a view-model test, and — the part that is easy to forget — cancels every
 * [PracticeFixture] built during the test *before* putting Main back.
 *
 * This exists because forgetting the second half is a real, recurring defect in this repository rather
 * than a hypothetical one. A view model whose scope is never cancelled keeps running coroutines on
 * `Dispatchers.Main` after its test returns; `resetMain()` then swaps the dispatcher out from under
 * them and the run fails inside `TestMainDispatcher` with "Dispatchers.Main is used concurrently with
 * setting it". It depends on nothing but timing, so it fires under load and passes on the retry — the
 * shape of failure that gets called a flake and is not one.
 *
 * It has been diagnosed here twice. `PracticeViewModelTest` carries the original write-up and its own
 * fix, which worked and stayed local to that one class; the other eight classes using [PracticeFixture]
 * kept the bare `setMain`/`resetMain` pair, and one of them, `M2IntroTest`, duly failed in CI on
 * 2026-08-22. Putting the ordering inside a rule is what stops the next class from getting it wrong:
 * there is no longer a version of this that a test can write incorrectly, because there is nothing to
 * write.
 *
 * Main is a *real* dispatcher, not a virtual-time `TestDispatcher`, for the reason `PracticeViewModelTest`
 * gives: `PracticeLoopEngine` genuinely pre-renders on `Dispatchers.Default`, so a virtual-time
 * scheduler on Main alone cannot wait for that cross-dispatcher work.
 *
 * docs/10-TESTING.md §3: "A flaky test in this project is a bug in the test or a determinism violation
 * in the code. Do not add retries. Find it."
 *
 * Public rather than `internal`, and that is load-bearing: JUnit finds a rule by reflection over a
 * public getter, and an internal property's getter is name-mangled, so an internal rule is a rule that
 * silently never runs — which would leave every test here exactly as racy as before while looking fixed.
 */
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(Dispatchers.Default)
    }

    override fun finished(description: Description) {
        // Order is the whole point: cancel what is running on Main, then take Main away.
        PracticeFixture.clearAll()
        Dispatchers.resetMain()
    }
}
