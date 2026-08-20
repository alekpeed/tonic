package com.tonic.feature.practice.engine.golden

import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.time.Clock
import com.tonic.feature.practice.engine.FakeAttemptRepository
import com.tonic.feature.practice.engine.FakeAudioInterruptions
import com.tonic.feature.practice.engine.FakeAudioPlayer
import com.tonic.feature.practice.engine.FakeConfusionRepository
import com.tonic.feature.practice.engine.FakeSessionRepository
import com.tonic.feature.practice.engine.FakeSkillStateRepository
import com.tonic.feature.practice.engine.PracticeLoopEngine
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * A full `M2` session driven through the **real** [PracticeLoopEngine], rendered as a stable text
 * trace — the behavioral baseline for the loop refactor that makes the engine polymorphic over item
 * types.
 *
 * The Stage 2.0 golden corpus proves *generation* is unchanged, and it cannot prove this: it calls
 * `M2ItemGenerator` directly and never touches the loop. What the loop adds on top is the part most at
 * risk here — session composition, warm-up handling, the pre-render pipeline, answer recording, the
 * adaptation that feeds the next item's axis levels, and the order all of that happens in. A refactor
 * that widened the item type while quietly reordering any of it would leave the corpus perfectly green.
 *
 * So this drives a scripted learner through a whole session and records what the loop actually did, per
 * item. Recorded through the public surface only — nothing here reaches into engine internals, so the
 * trace stays valid across a refactor that changes those internals, which is the entire point.
 */
object M2SessionTrace {
    /**
     * Deliberately mixed: three right, one wrong, repeating. A perfect learner would drive the
     * staircase in one direction only and never exercise a decrease, and a random one would not be
     * reproducible.
     */
    private fun answerFor(
        index: Int,
        correctLabel: String,
        alternatives: List<String>,
    ): String =
        if (index % 4 == 3) {
            alternatives.firstOrNull { it != correctLabel } ?: correctLabel
        } else {
            correctLabel
        }

    suspend fun render(): String {
        val attempts = FakeAttemptRepository()
        val skillStates = FakeSkillStateRepository(attempts)
        val confusion = FakeConfusionRepository()
        val sessions = FakeSessionRepository()
        val player = FakeAudioPlayer()
        val interruptions = FakeAudioInterruptions()
        var now = Instant.EPOCH

        val engine =
            PracticeLoopEngine(
                attempts,
                skillStates,
                confusion,
                sessions,
                player,
                interruptions,
                Clock { now },
            )

        engine.start(
            currentNode =
                SkillWorkContext(
                    skillId = SkillIds.M2_DEG_SET_4,
                    axisLevels = DifficultyAxis.RECOGNITION_AXES.associateWith { 0 },
                    totalAttempts = 0,
                ),
            dueReviews = emptyList(),
            sessionLengthMinutes = SESSION_MINUTES,
            rootSeed = ROOT_SEED,
            now = now,
        )

        val out = StringBuilder()
        out.appendLine("## M2 session trace: skill=M2.DEG_SET_4 rootSeed=$ROOT_SEED minutes=$SESSION_MINUTES")

        var index = 0
        while (!engine.state.value.isFinished && index < MAX_ITEMS) {
            val item = engine.state.value.currentItem ?: break
            val correct = item.targetDegree.canonicalLabel
            val response = answerFor(index, correct, item.activeDegrees.map { it.canonicalLabel })

            out.appendLine(
                "[$index] key=${item.key.value} mode=${item.mode} target=$correct midi=${item.targetMidi} " +
                    "fade=${item.referencePlan.cadenceFadeLevel} refElements=${item.referencePlan.elements.size} " +
                    "reminder=${item.homeReminder != null} timbre=${item.timbre} " +
                    "activeDegrees=${item.activeDegrees.joinToString("/") { it.canonicalLabel }} " +
                    "answered=$response",
            )

            // A steady, non-zero pace so the wall-clock budget behaves as it would in a real session.
            now = now.plusSeconds(SECONDS_PER_ITEM)
            engine.submitAnswer(response)
            index++
        }

        engine.awaitPersistence()

        val finalState = skillStates.observe(SkillIds.M2_DEG_SET_4).first()
        out.appendLine("-- final --")
        out.appendLine("itemsAnswered=$index")
        out.appendLine("attemptsRecorded=${attempts.all.size}")
        out.appendLine("masteryState=${finalState.masteryState}")
        out.appendLine("totalAttempts=${finalState.totalAttempts}")
        out.appendLine(
            "axisLevels=" +
                DifficultyAxis.RECOGNITION_AXES.joinToString(",") { "${it.name}=${finalState.axisLevels[it]}" },
        )
        out.appendLine("activeAxis=${finalState.activeAxis}")

        engine.close()
        return out.toString()
    }

    private const val ROOT_SEED = 20_260_820_777L
    private const val SESSION_MINUTES = 5
    private const val SECONDS_PER_ITEM = 6L

    /** A bound, not an expectation — the session ends on its own; this only stops a runaway loop. */
    private const val MAX_ITEMS = 200
}
