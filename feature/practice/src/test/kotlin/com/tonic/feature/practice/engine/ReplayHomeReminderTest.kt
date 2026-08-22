package com.tonic.feature.practice.engine

import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Replay on a silent item must bring home back — reported from live use as "it's only giving me one
 * note and saying 'guess where this is?' I don't know, in reference to what?". An L6/L7 audiation
 * block item (and an L1 group item) deliberately plays no reference of its own; replaying the bare
 * note again answers nothing. [PracticeLoopEngine.replay] therefore plays the item's
 * [Item.FunctionalRecognitionItem.homeReminder] in front of the target on exactly those items.
 */
class ReplayHomeReminderTest {
    private class Fixture(
        var now: Instant = Instant.EPOCH,
    ) {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val confusionRepository = FakeConfusionRepository()
        val sessionRepository = FakeSessionRepository()
        val audioPlayer = FakeAudioPlayer()
        val audioInterruptions = FakeAudioInterruptions()
        val clock = Clock { now }
        val engine =
            PracticeLoopEngine(
                attemptRepository,
                skillStateRepository,
                confusionRepository,
                sessionRepository,
                audioPlayer,
                audioInterruptions,
                clock,
            )
    }

    /**
     * Drives a session started at CADENCE_FADE L6 to the first silent (block-continuation) item,
     * skipping past the warmup slots and the block-start item. Items are abandoned rather than
     * answered, so no adaptation runs and the composed levels hold.
     */
    private suspend fun Fixture.advanceToSilentItem(): Item.FunctionalRecognitionItem {
        engine.start(
            SkillWorkContext(
                SkillIds.M2_DEG_SET_1,
                axisLevels =
                    DifficultyAxis.entries.associateWith { 0 } +
                        (DifficultyAxis.CADENCE_FADE to 6),
                totalAttempts = 100,
            ),
            dueReviews = emptyList(),
            sessionLengthMinutes = 5,
            rootSeed = 41L,
            now = now,
        )
        repeat(20) {
            val item = engine.state.value.recognitionItem ?: fail("session ended before a silent item appeared")
            if (item.referencePlan.elements.isEmpty()) return item
            engine.abandonCurrentItem()
        }
        fail("no silent item within 20 items of an L6 session - audiation blocks should yield 7 per 8")
    }

    @Test
    fun `replay on a silent item plays the home reminder, not the bare note again`() =
        runBlocking {
            val fixture = Fixture()
            val item = fixture.advanceToSilentItem()
            assertNotNull(item.homeReminder, "a silent block item must carry its way back home")

            val ownBuffer = fixture.audioPlayer.playedBuffers.last()
            fixture.engine.replay()
            val replayed = fixture.audioPlayer.playedBuffers.last()

            assertTrue(
                replayed.samples.size > ownBuffer.samples.size,
                "the replay must prepend the home reminder - got ${replayed.durationMs}ms " +
                    "against the item's own ${ownBuffer.durationMs}ms",
            )
        }

    @Test
    fun `replay on an item that plays its own reference is byte-identical to the first presentation`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                SkillWorkContext(
                    SkillIds.M2_DEG_SET_1,
                    axisLevels = DifficultyAxis.entries.associateWith { 0 },
                    totalAttempts = 0,
                ),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 42L,
                now = fixture.now,
            )
            val item = fixture.engine.state.value.recognitionItem
            assertNotNull(item)
            assertEquals(null, item.homeReminder, "an item with a full reference needs no reminder")

            val ownBuffer = fixture.audioPlayer.playedBuffers.last()
            fixture.engine.replay()

            assertEquals(
                ownBuffer,
                fixture.audioPlayer.playedBuffers.last(),
                "replay of a self-contained item is the same stimulus, unchanged (CLAUDE.md §5)",
            )
        }
}
