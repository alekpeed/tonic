package com.tonic.feature.practice.ui

import com.tonic.core.model.items.AxisChange
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.Session
import com.tonic.core.ui.components.PlaybackPhase

/** Everything docs/08-UI-SPEC.md §4's Practice screen needs to render one moment of a session. */
data class PracticeUiState(
    val item: Item.FunctionalRecognitionItem? = null,
    val labelStyle: LabelStyle = LabelStyle.NUMBERS,
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val phase: PlaybackPhase = PlaybackPhase.REFERENCE,
    val itemsCompleted: Int = 0,
    val itemsPlanned: Int = 0,
    /** The degree the learner tapped for the item currently on screen, if any. Cleared on advance. */
    val selectedDegree: ScaleDegree? = null,
    /** Set once feedback is in for the item currently on screen - null means "not answered yet." */
    val correctDegree: ScaleDegree? = null,
    /** False while audio is still playing (docs/08-UI-SPEC.md §3's `disabled` state) or while feedback for the previous answer is still resolving. */
    val inputEnabled: Boolean = false,
    val isFinished: Boolean = false,
    val isLoading: Boolean = true,
    /** The current session's persisted id, once known - `summary/{sessionId}`'s own nav argument. */
    val sessionId: Long? = null,
    /**
     * An interrupted session found at startup, waiting on the user's choice — docs/10-TESTING.md §11's
     * "force stop mid-session -> resume offered." Non-null means nothing has started yet: the screen is
     * showing the offer, not a live item.
     */
    val resumableSession: Session? = null,
    /** True while an interruption (docs/06-AUDIO-ENGINE.md §8) has paused the loop. The session is not over. */
    val isPaused: Boolean = false,
    /** Non-null only on the item where an axis moved - docs/11-ONBOARDING-CLARITY.md §9.3. */
    val axisChange: AxisChange? = null,
) {
    val activeDegrees: List<ScaleDegree> get() = item?.activeDegrees ?: emptyList()
}
