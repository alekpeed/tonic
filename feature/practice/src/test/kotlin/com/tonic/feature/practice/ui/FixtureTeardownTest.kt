package com.tonic.feature.practice.ui

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That [MainDispatcherRule] and [PracticeFixture.clear] actually cancel something.
 *
 * This class exists because the failure it guards against is invisible: a rule that never fires, or a
 * `clear` that cancels nothing, leaves every test in this package exactly as racy as before while the
 * suite stays green. The original defect was already diagnosed once, fixed in one class, and left in
 * eight others — and it took a CI failure on `M2IntroTest` to notice. Asserting the mechanism rather
 * than trusting it is the difference between a fix and a fix-shaped comment.
 *
 * docs/21-HANDOFF.md §8, generalized: state the route, then check the route.
 */
@RunWith(AndroidJUnit4::class)
class FixtureTeardownTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun `clearing a fixture cancels the view model scope it left running`() =
        runBlocking {
            val fixture = PracticeFixture()
            fixture.viewModel.startIfNeeded()
            // Wait for a live item, so there is genuinely work in flight - the phase timer this test is
            // about only exists once an item is being presented.
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }

            val job = fixture.viewModel.viewModelScope.coroutineContext.job
            assertTrue(job.isActive, "the view model should still have work in flight before teardown")

            fixture.clear()

            assertFalse(job.isActive, "clear() must cancel the scope, not merely ask it to stop")
            assertTrue(job.isCompleted, "clear() must wait for the scope, or resetMain still races it")
        }

    @Test
    fun `clearAll drains every fixture a test built, not just the last one`() =
        runBlocking {
            // Tests here build more than one fixture (a "same device, later launch" pair, for instance).
            // Draining only the most recent would leave the earlier one running on Main and reintroduce
            // the race for exactly the tests most likely to hit it.
            val first = PracticeFixture()
            val second = PracticeFixture(shared = first)
            first.viewModel.startIfNeeded()
            second.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { first.viewModel.uiState.first { it.item != null } }
            withTimeout(TIMEOUT_MS) { second.viewModel.uiState.first { it.item != null } }

            val jobs =
                listOf(first, second).map { it.viewModel.viewModelScope.coroutineContext.job }
            assertTrue(jobs.all { it.isActive }, "both fixtures should be live before teardown")

            PracticeFixture.clearAll()

            assertTrue(jobs.all { it.isCompleted }, "clearAll must drain every fixture built during the test")
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
