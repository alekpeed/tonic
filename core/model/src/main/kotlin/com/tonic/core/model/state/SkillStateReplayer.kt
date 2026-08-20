package com.tonic.core.model.state

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId

/**
 * Replays one skill's complete attempt history into a materialized
 * [SkillState] — the algorithmic core of `SkillStateRepository.rebuildFromAttempts()`
 * (docs/05-DATA-MODEL.md §5, docs/09-BUILD-PLAN.md Stage 5 acceptance:
 * "reconstructs skill state identically to incremental updates").
 *
 * Declared here rather than in `:core:data` because the actual replay
 * logic (staircase, mastery evaluation, FSRS) belongs to `:core:engine`,
 * and docs/04-ARCHITECTURE.md §2 permits `:core:data` to depend only on
 * `:core:model` — not `:core:engine`. `:core:data` depends on this
 * interface only; `:core:engine` provides the implementation
 * ([com.tonic.core.engine.replay.SkillStateReducer]); the composition root
 * wires the two together via DI. This is the standard dependency-inversion
 * answer to a case where the module graph would otherwise need a cycle
 * (CLAUDE.md §4: "if the layering blocks something, stop and ask" — the
 * alternatives, `:core:data` importing `:core:engine` or duplicating
 * adaptive-engine algorithms inside `:core:data`, would violate the
 * one-way dependency rule or CLAUDE.md's ban on duplicating logic).
 */
fun interface SkillStateReplayer {
    /** [attempts] must be every attempt recorded for [skillId], in chronological order. */
    fun replay(
        skillId: SkillId,
        attempts: List<Attempt>,
    ): SkillState
}
