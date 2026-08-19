package com.tonic.app.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.app.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** Robolectric, for the same reason every other ViewModel test in this module needs it - `viewModelScope` requires a real `Main` dispatcher. */
@RunWith(AndroidJUnit4::class)
class OnboardingViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onDone persists onboardingCompleted, then invokes the caller's continuation`() =
        runBlocking {
            val settingsRepository = FakeSettingsRepository()
            val viewModel = OnboardingViewModel(settingsRepository)

            val callbackRan = CompletableDeferred<Unit>()
            viewModel.onDone { callbackRan.complete(Unit) }
            withTimeout(TIMEOUT_MS) { callbackRan.await() }

            assertTrue(settingsRepository.settings.value.onboardingCompleted, "the flag must be written")
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
