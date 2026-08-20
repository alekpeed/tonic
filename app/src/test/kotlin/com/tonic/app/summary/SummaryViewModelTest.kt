package com.tonic.app.summary

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.app.FakeSessionRepository
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
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SummaryViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the completed and planned item counts for the session named by the nav argument`() =
        runBlocking {
            val sessionRepository = FakeSessionRepository()
            val session = sessionRepository.create(rootSeed = 1L, plannedItemCount = 50, startedAt = Instant.EPOCH)
            sessionRepository.updateResumeState(session.id!!, completedItemCount = 37, resumeState = null)

            val savedStateHandle = SavedStateHandle(mapOf(SummaryViewModel.SESSION_ID_ARG to session.id))
            val viewModel = SummaryViewModel(sessionRepository, savedStateHandle)

            val state = viewModel.uiState.first { !it.isLoading }
            assertEquals(37, state.itemsCompleted)
            assertEquals(50, state.itemsPlanned)
        }
}
