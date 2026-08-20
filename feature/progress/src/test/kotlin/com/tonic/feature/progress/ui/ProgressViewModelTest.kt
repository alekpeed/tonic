package com.tonic.feature.progress.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.ConfusionCell
import com.tonic.core.model.state.ConfusionMatrix
import com.tonic.core.model.state.FsrsState
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 9 acceptance: "mastery map shows the blocking criterion in plain
 * language," "per-degree accuracy accurate against a known attempt log," "confusion view produces
 * correct plain-language statements." Every expected value below is hand-computed from the fixture,
 * not just re-derived from the production code under test.
 */
@RunWith(AndroidJUnit4::class)
class ProgressViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun attempt(
        skillId: SkillId,
        targetLabel: String,
        correct: Boolean,
        cadenceFadeLevel: Int = 0,
        isIndependenceCheckProbe: Boolean = false,
    ) = Attempt(
        skillId = skillId,
        sessionId = 1L,
        itemSeed = 1L,
        axisLevels = DifficultyAxis.entries.associateWith { 0 },
        targetLabel = targetLabel,
        responseLabel = if (correct) targetLabel else "x",
        correct = correct,
        latencyMs = 500L,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 60,
        timbreId = "PURE",
        cadenceFadeLevel = cadenceFadeLevel,
        timestamp = Instant.EPOCH,
        isIndependenceCheckProbe = isIndependenceCheckProbe,
    )

    private class Fixture {
        val skillStateRepository = FakeSkillStateRepository()
        val attemptRepository = FakeAttemptRepository()
        val confusionRepository = FakeConfusionRepository()
        val settingsRepository = FakeSettingsRepository()
        val viewModel =
            ProgressViewModel(skillStateRepository, attemptRepository, confusionRepository, settingsRepository)
    }

    private fun masteredState(skillId: SkillId) =
        SkillState.initial(skillId).copy(
            masteryState = MasteryState.MASTERED,
            fsrs = FsrsState(stability = 1.0, difficulty = 1.0, lastReview = Instant.EPOCH, due = Instant.EPOCH),
        )

    @Test
    fun `mastery map shows the blocking criterion for the in-progress node in plain language`() =
        runBlocking {
            val fixture = Fixture()
            fixture.skillStateRepository.setState(masteredState(SkillIds.M2_DEG_SET_1))
            fixture.skillStateRepository.setState(
                SkillState.initial(SkillIds.M2_DEG_SET_2).copy(masteryState = MasteryState.IN_PROGRESS),
            )
            // 26 of 30 correct = 86.66...% - below the 90% floor, and the very first criterion
            // MasteryEvaluator checks, so it is guaranteed to be the reported blocking criterion.
            val window =
                (1..30).map { i ->
                    attempt(SkillIds.M2_DEG_SET_2, targetLabel = "1", correct = i > 4)
                }
            fixture.attemptRepository.setAttempts(SkillIds.M2_DEG_SET_2, window)

            fixture.viewModel.loadIfNeeded()
            val state = fixture.viewModel.uiState.first { !it.isLoading }

            val masteredNode = state.masteryMap.first { it.skillId == SkillIds.M2_DEG_SET_1 }
            assertEquals(MasteryState.MASTERED, masteredNode.masteryState)
            assertNull(masteredNode.verdict, "a mastered node has nothing left to block on")

            val inProgressNode = state.masteryMap.first { it.skillId == SkillIds.M2_DEG_SET_2 }
            val blocking = assertNotNull(inProgressNode.verdict?.blockingCriterion)
            assertEquals(MasteryCriterion.Kind.OVERALL_ACCURACY, blocking.kind)
            val copy = copyFor(blocking, com.tonic.core.model.state.LabelStyle.NUMBERS)
            assertEquals(listOf(87, 90), copy.args, "87% measured, 90% required - the docs' own worked example")

            val lockedNode = state.masteryMap.first { it.skillId == SkillIds.M2_DEG_SET_3 }
            assertEquals(MasteryState.LOCKED, lockedNode.masteryState)
            assertNull(lockedNode.verdict)
        }

    @Test
    fun `per-degree accuracy is computed correctly against a known confusion matrix`() =
        runBlocking {
            val fixture = Fixture()
            fixture.skillStateRepository.setState(masteredState(SkillIds.M2_DEG_SET_1))
            fixture.skillStateRepository.setState(
                SkillState.initial(SkillIds.M2_DEG_SET_2).copy(masteryState = MasteryState.IN_PROGRESS),
            )
            fixture.confusionRepository.setMatrix(
                ConfusionMatrix(
                    SkillIds.M2_DEG_SET_2,
                    listOf(
                        ConfusionCell("1", "1", count = 10, windowCount = 10, updatedAt = Instant.EPOCH),
                        ConfusionCell("2", "2", count = 7, windowCount = 7, updatedAt = Instant.EPOCH),
                        ConfusionCell("2", "1", count = 3, windowCount = 3, updatedAt = Instant.EPOCH),
                        ConfusionCell("5", "5", count = 8, windowCount = 8, updatedAt = Instant.EPOCH),
                        ConfusionCell("5", "3", count = 2, windowCount = 2, updatedAt = Instant.EPOCH),
                    ),
                ),
            )

            fixture.viewModel.loadIfNeeded()
            val state = fixture.viewModel.uiState.first { !it.isLoading }

            val byDegree = state.degreeAccuracy.associate { it.degree to it.accuracy }
            assertEquals(1.0, byDegree.getValue(ScaleDegree(1)))
            assertEquals(0.7, byDegree.getValue(ScaleDegree(2)))
            assertNull(byDegree.getValue(ScaleDegree(3)), "no attempts recorded for degree 3 - no data, not zero")
            assertEquals(0.8, byDegree.getValue(ScaleDegree(5)))

            assertEquals(
                listOf(ScaleDegree(2) to ScaleDegree(1), ScaleDegree(5) to ScaleDegree(3)),
                state.confusionStatements.map { it.target to it.response },
            )
        }

    @Test
    fun `the independence check recomputes pass-fail from the most recent 30 probe attempts`() =
        runBlocking {
            val fixture = Fixture()
            val probes =
                (1..30).map { i ->
                    attempt(
                        SkillIds.M2_FULL_DIATONIC,
                        "1",
                        correct = i > 3,
                        cadenceFadeLevel = 6,
                        isIndependenceCheckProbe = true,
                    )
                }
            fixture.attemptRepository.setAttempts(SkillIds.M2_FULL_DIATONIC, probes)

            fixture.viewModel.loadIfNeeded()
            val state = fixture.viewModel.uiState.first { !it.isLoading }

            val summary = assertNotNull(state.independenceCheck)
            assertEquals(IndependenceCheckStatus.PASSED, summary.status)
            assertEquals(27.0 / 30.0, summary.accuracy)
        }

    @Test
    fun `no independence-check probes yet reports not-yet-attempted, not a failure`() =
        runBlocking {
            val fixture = Fixture()
            fixture.viewModel.loadIfNeeded()
            val state = fixture.viewModel.uiState.first { !it.isLoading }
            assertNull(state.independenceCheck)
        }

    @Test
    fun `a settings change to label style is reflected live in the ui state`() =
        runBlocking {
            val fixture = Fixture()
            fixture.viewModel.loadIfNeeded()
            fixture.viewModel.uiState.first { !it.isLoading }

            fixture.settingsRepository.setLabelStyle(com.tonic.core.model.state.LabelStyle.SOLFEGE)
            val updated =
                fixture.viewModel.uiState.first { it.labelStyle == com.tonic.core.model.state.LabelStyle.SOLFEGE }
            assertTrue(updated.labelStyle == com.tonic.core.model.state.LabelStyle.SOLFEGE)
        }
}
