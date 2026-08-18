package com.tonic.feature.practice.engine

import com.tonic.core.model.items.Item

/** What a caller (Stage 7's ViewModel, or a headless test harness) needs to render one moment of a session. */
data class PracticeLoopState(
    val currentItem: Item.FunctionalRecognitionItem? = null,
    /** True while [currentItem] is one of the 30 forced-L6 `M2.INDEPENDENCE_CHECK` probes, not ordinary practice. */
    val isIndependenceCheckProbe: Boolean = false,
    val itemsCompleted: Int = 0,
    /** The number of ordinary (non-independence-check) slots originally planned for this session. */
    val itemsPlanned: Int = 0,
    val lastFeedback: AnswerFeedback? = null,
    val isFinished: Boolean = false,
)

/** Shown briefly after [PracticeLoopEngine.submitAnswer], before the next item starts. */
data class AnswerFeedback(
    val correct: Boolean,
    val correctLabel: String,
)
