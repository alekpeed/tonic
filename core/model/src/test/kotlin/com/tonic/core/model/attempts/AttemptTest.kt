package com.tonic.core.model.attempts

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse

class AttemptTest {
    @Test
    fun `defaults are not warmup and not abandoned`() {
        val attempt =
            Attempt(
                skillId = SkillIds.M2_DEG_SET_1,
                sessionId = 1L,
                itemSeed = 2L,
                axisLevels = mapOf(DifficultyAxis.CADENCE_FADE to 3),
                targetLabel = "3",
                responseLabel = "3",
                correct = true,
                latencyMs = 850,
                replayCount = 0,
                keyPitchClass = 0,
                targetMidi = 64,
                timbreId = "SOFT",
                cadenceFadeLevel = 3,
                timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            )
        assertFalse(attempt.isWarmup)
        assertFalse(attempt.isAbandoned)
    }
}
