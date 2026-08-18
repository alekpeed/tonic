package com.tonic.feature.diagnostic.engine

import com.tonic.core.model.items.Item
import com.tonic.core.model.state.DiagnosticResult

/** Which M0 sub-test is currently running — docs/03-CURRICULUM.md §3, administered in this order. */
enum class M0SubTest { PITCH_DIRECTION, SAME_DIFFERENT, TONAL_MEMORY, AMUSIA_SCREEN }

/** What a caller (Stage 8's ViewModel, or a headless test) needs to render one moment of a diagnostic run. */
data class DiagnosticLoopState(
    val currentItem: Item? = null,
    val currentSubTest: M0SubTest? = null,
    /** 0-based index of [currentSubTest] among the 4 - a coarse, honest progress signal since each sub-test's own length is adaptive and unknown in advance. */
    val subTestIndex: Int = 0,
    val totalSubTests: Int = M0SubTest.entries.size,
    /** False while [currentItem]'s audio is still playing - docs/08-UI-SPEC.md §1's large-touch-target answer widgets accept no input until the sound is fully heard. */
    val inputEnabled: Boolean = false,
    val isFinished: Boolean = false,
    /** Non-null only once [isFinished] - docs/03-CURRICULUM.md §3's full output contract. */
    val result: DiagnosticResult? = null,
)
