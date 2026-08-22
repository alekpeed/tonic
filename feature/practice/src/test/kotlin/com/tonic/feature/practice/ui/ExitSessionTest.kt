package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.state.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * docs/08-UI-SPEC.md §2a — leaving Practice mid-session, from the visible control or the system back
 * gesture, must save the session for resume before navigation happens, and the interrupted item must
 * be recorded abandoned rather than scored.
 */
@RunWith(AndroidJUnit4::class)
class ExitSessionTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }

    @Test
    fun `exiting mid-item persists resume state BEFORE the navigation callback runs`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.startPastIntro(TIMEOUT_MS)

            val exited = CompletableDeferred<Unit>()
            var resumableAtNavigation: Boolean? = null
            fixture.viewModel.onExitSession {
                // What the NavGraph callback would observe at the moment it navigates.
                resumableAtNavigation =
                    runBlocking { fixture.sessionRepository.findResumable()?.resumeState != null }
                exited.complete(Unit)
            }
            withTimeout(TIMEOUT_MS) { exited.await() }

            assertEquals(
                true,
                resumableAtNavigation,
                "§2a: the save must be sequenced before navigation - navigating first would tear the " +
                    "ViewModel down and race the write that makes the session resumable",
            )
        }

    @Test
    fun `the interrupted item is abandoned, never scored, and the session is offered back next launch`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.startPastIntro(TIMEOUT_MS)

            val exited = CompletableDeferred<Unit>()
            fixture.viewModel.onExitSession { exited.complete(Unit) }
            withTimeout(TIMEOUT_MS) { exited.await() }

            val recorded = fixture.attemptRepository.all.single()
            assertTrue(recorded.isAbandoned, "leaving mid-item must not score the item the user walked out on")

            // The next launch: a fresh ViewModel over the same repositories offers the session back.
            val next = PracticeFixture(AppSettings(module2IntroSeen = true), shared = fixture)
            next.viewModel.startIfNeeded()
            val offered =
                withTimeout(TIMEOUT_MS) { next.viewModel.uiState.first { it.resumableSession != null } }
            assertNotNull(offered.resumableSession, "§2a: nothing about leaving may lose progress")
            Unit
        }
}
