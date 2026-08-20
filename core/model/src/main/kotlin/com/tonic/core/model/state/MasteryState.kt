package com.tonic.core.model.state

/** A skill node's progression status — the `masteryStatus` column, docs/05-DATA-MODEL.md §1. */
enum class MasteryState {
    /** Prerequisite not yet met. */
    LOCKED,

    /** Prerequisite met, not yet started. */
    AVAILABLE,

    /** Being actively practiced. */
    IN_PROGRESS,

    /** All five docs/03-CURRICULUM.md §5.5 criteria hold; now under FSRS review. */
    MASTERED,
}
