package com.tonic.app.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.app.FakeSessionRepository
import com.tonic.app.FakeSettingsRepository
import com.tonic.app.FakeSkillStateRepository
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // A fixed noon-UTC instant. The streak test below derives "today" through the same
    // `ZoneId.systemDefault()` the production code uses, rather than assuming a host timezone, so this
    // stays correct regardless of what zone the test happens to run in.
    private val now = Instant.parse("2026-08-19T12:00:00Z")

    private class Fixture(
        settingsInitial: AppSettings = AppSettings(diagnosticCompleted = true),
        now: Instant,
    ) {
        val sessionRepository = FakeSessionRepository()
        val skillStateRepository = FakeSkillStateRepository()
        val settingsRepository = FakeSettingsRepository(settingsInitial)
        val clock = Clock { now }
        val viewModel = HomeViewModel(settingsRepository, skillStateRepository, sessionRepository, clock)
    }

    @Test
    fun `before the diagnostic has ever completed, home reports needsDiagnostic`() =
        runBlocking {
            val fixture = Fixture(settingsInitial = AppSettings(diagnosticCompleted = false), now = now)
            fixture.viewModel.loadIfNeeded()
            val state = fixture.viewModel.uiState.first { !it.isLoading }
            assertTrue(state.needsDiagnostic)
        }

    @Test
    fun `once the diagnostic is complete, home reports the current in-progress node's active degrees`() =
        runBlocking {
            val fixture = Fixture(now = now)
            fixture.skillStateRepository.setState(
                SkillState.initial(SkillIds.M2_DEG_SET_1).copy(masteryState = MasteryState.IN_PROGRESS),
            )

            fixture.viewModel.loadIfNeeded()
            val state = fixture.viewModel.uiState.first { !it.isLoading }

            assertFalse(state.needsDiagnostic)
            assertEquals(listOf(1, 3, 5), state.currentActiveDegrees.map { it.degree })
            assertEquals(MasteryState.IN_PROGRESS, state.currentNodeMasteryState)
        }

    @Test
    fun `streak reflects consecutive practiced days, computed against the injected clock's today`() =
        runBlocking {
            val fixture = Fixture(now = now)
            val zone = ZoneId.systemDefault()
            val today = now.atZone(zone).toLocalDate()
            fixture.sessionRepository.seedCompleted(today.atStartOfDay(zone).toInstant())
            fixture.sessionRepository.seedCompleted(today.minusDays(1).atStartOfDay(zone).toInstant())
            fixture.sessionRepository.seedCompleted(today.minusDays(2).atStartOfDay(zone).toInstant())

            fixture.viewModel.loadIfNeeded()
            val state = fixture.viewModel.uiState.first { !it.isLoading }

            assertEquals(3, state.streakDays)
        }
}
