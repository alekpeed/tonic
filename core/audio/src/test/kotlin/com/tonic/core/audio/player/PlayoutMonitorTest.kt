package com.tonic.core.audio.player

import com.tonic.core.audio.synth.PcmBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The regression these guard is the one that made the app silent on a device: playback was torn down
 * as soon as the frames had been *written*, so a clip was cut off after a few milliseconds — nothing
 * at all on a cold audio path, a brief fragment once warm. [PlayoutMonitor] is the piece that decides
 * "the head has actually played every frame," so its two failure modes — quitting early and never
 * quitting — are what get asserted here.
 */
class PlayoutMonitorTest {
    private val pollMs = 10L

    /** Drives a monitor with a head that advances [framesPerPoll] per poll, returning the verdict sequence. */
    private fun run(
        totalFrames: Int,
        maxPolls: Int,
        framesPerPoll: Int,
        polls: Int,
    ): List<PlayoutMonitor.Verdict> {
        val monitor = PlayoutMonitor(totalFrames, maxPolls)
        val verdicts = mutableListOf<PlayoutMonitor.Verdict>()
        var head = 0
        repeat(polls) {
            val verdict = monitor.observe(head)
            verdicts += verdict
            if (verdict != PlayoutMonitor.Verdict.KEEP_WAITING) return verdicts
            head += framesPerPoll
        }
        return verdicts
    }

    @Test
    fun `waits while the head is still short of the last written frame`() {
        val verdicts = run(totalFrames = 1000, maxPolls = 500, framesPerPoll = 100, polls = 5)
        assertTrue(
            verdicts.all { it == PlayoutMonitor.Verdict.KEEP_WAITING },
            "tearing down here is exactly the bug: the frames are written but not yet heard",
        )
    }

    @Test
    fun `reports done only once the head reaches the last written frame`() {
        val verdicts = run(totalFrames = 1000, maxPolls = 500, framesPerPoll = 100, polls = 20)
        assertEquals(PlayoutMonitor.Verdict.DONE, verdicts.last())
        assertEquals(11, verdicts.size, "10 polls of 100 frames to cover 1000, then the arrival poll")
    }

    @Test
    fun `a head that never moves does not stall the loop forever`() {
        val verdicts = run(totalFrames = 1000, maxPolls = 30, framesPerPoll = 0, polls = 100)
        assertEquals(PlayoutMonitor.Verdict.GIVE_UP, verdicts.last())
        assertEquals(30, verdicts.size, "gives up at the poll budget, not before and not never")
    }

    /**
     * The audio HAL can take ~100ms to spin up before the playback head moves at all on the first note
     * of a session. An earlier design treated that as a stall and bailed — which reproduces the
     * original bug precisely on the one item where it hurts most. A slow start must still play out.
     */
    @Test
    fun `a slow cold start is waited through rather than treated as a failure`() {
        val monitor = PlayoutMonitor(totalFrames = 1000, maxPolls = PlayoutMonitor.pollsFor(1000.0, pollMs))
        repeat(15) {
            assertEquals(
                PlayoutMonitor.Verdict.KEEP_WAITING,
                monitor.observe(0),
                "150ms of HAL warm-up is normal, not a dead track",
            )
        }
        assertEquals(PlayoutMonitor.Verdict.DONE, monitor.observe(1000))
    }

    @Test
    fun `the poll budget covers the clip's own duration plus a warm-up margin`() {
        val polls = PlayoutMonitor.pollsFor(durationMs = 2_000.0, pollIntervalMs = pollMs)
        assertEquals(275, polls, "2000ms of audio + 750ms margin, at 10ms per poll")
        assertTrue(polls * pollMs > 2_000, "the budget must never expire before the clip could finish")
    }

    @Test
    fun `even a zero-length clip gets at least one poll`() {
        assertEquals(1, PlayoutMonitor.pollsFor(durationMs = 0.0, pollIntervalMs = 1_000L))
    }

    /** A real M0 diagnostic clip - two tones plus a gap - must be given its full length to sound. */
    @Test
    fun `a realistic diagnostic clip gets a budget longer than the clip itself`() {
        val twoTonesAndAGap = PcmBuffer.silence(PcmBuffer.msToSamples(1_300L))
        val budgetMs = PlayoutMonitor.pollsFor(twoTonesAndAGap.durationMs, pollMs) * pollMs
        assertTrue(
            budgetMs >= twoTonesAndAGap.durationMs,
            "budget ${budgetMs}ms must cover the ${twoTonesAndAGap.durationMs}ms clip",
        )
    }
}
