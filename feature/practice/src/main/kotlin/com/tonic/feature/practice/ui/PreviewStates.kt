package com.tonic.feature.practice.ui

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ItemTiming
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.ui.components.PlaybackPhase

/** Sample [PracticeUiState]s for `@Preview`s only - never referenced by production code. */
internal object PreviewStates {
    private fun item(
        activeDegrees: List<Int>,
        targetDegree: Int,
    ) = Item.FunctionalRecognitionItem(
        skill = if (activeDegrees.size == 7) SkillIds.M2_FULL_DIATONIC else SkillIds.M2_DEG_SET_1,
        key = PitchClass.C,
        mode = Mode.MAJOR,
        targetDegree = ScaleDegree(targetDegree),
        targetMidi = 60 + ScaleDegree(targetDegree).semitoneOffset(Mode.MAJOR),
        referencePlan =
            ReferencePlan(
                cadenceFadeLevel = CadenceFadeLevel.L0,
                elements =
                    listOf(
                        ReferenceElement.ChordEvent(listOf(60, 64, 67), durationMs = 900, timbre = TimbreId.SOFT),
                    ),
            ),
        timbre = TimbreId.PURE,
        referenceTimbre = TimbreId.SOFT,
        timing = ItemTiming(referenceDurationMs = 900, gapAfterReferenceMs = 300, targetDurationMs = 700),
        activeDegrees = activeDegrees.map { ScaleDegree(it) },
        seed = 1L,
    )

    val awaitingAnswer =
        PracticeUiState(
            item = item(listOf(1, 3, 5), targetDegree = 3),
            labelStyle = LabelStyle.NUMBERS,
            phase = PlaybackPhase.AWAITING_ANSWER,
            itemsCompleted = 4,
            itemsPlanned = 54,
            inputEnabled = true,
            isLoading = false,
        )

    val correctFeedback =
        awaitingAnswer.copy(
            selectedDegree = ScaleDegree(3),
            correctDegree = ScaleDegree(3),
            inputEnabled = false,
        )

    val incorrectFeedback =
        awaitingAnswer.copy(
            selectedDegree = ScaleDegree(1),
            correctDegree = ScaleDegree(3),
            inputEnabled = false,
        )

    /** All twelve chromatic degrees — `M11.CHROM_FULL`, the widest answer set the ladder ever shows. */
    val fullChromaticSet =
        PracticeUiState(
            item =
                Item.FunctionalRecognitionItem(
                    skill = SkillIds.M11_CHROM_FULL,
                    key = PitchClass.C,
                    mode = Mode.MAJOR,
                    targetDegree = ScaleDegree(4, 1),
                    targetMidi = 60 + ScaleDegree(4, 1).semitoneOffset(Mode.MAJOR),
                    referencePlan =
                        ReferencePlan(
                            cadenceFadeLevel = CadenceFadeLevel.L0,
                            elements =
                                listOf(
                                    ReferenceElement.ChordEvent(
                                        listOf(60, 64, 67),
                                        durationMs = 900,
                                        timbre = TimbreId.SOFT,
                                    ),
                                ),
                        ),
                    timbre = TimbreId.PURE,
                    referenceTimbre = TimbreId.SOFT,
                    timing = ItemTiming(referenceDurationMs = 900, gapAfterReferenceMs = 300, targetDurationMs = 700),
                    activeDegrees = ScaleDegree.ALL_CHROMATIC.sortedBy { it.semitoneOffset(Mode.MAJOR) },
                    seed = 1L,
                ),
            labelStyle = LabelStyle.NUMBERS,
            phase = PlaybackPhase.AWAITING_ANSWER,
            itemsCompleted = 12,
            itemsPlanned = 54,
            inputEnabled = true,
            isLoading = false,
        )

    val fullDiatonicSet =
        PracticeUiState(
            item = item(listOf(1, 2, 3, 4, 5, 6, 7), targetDegree = 7),
            labelStyle = LabelStyle.SOLFEGE,
            phase = PlaybackPhase.AWAITING_ANSWER,
            itemsCompleted = 30,
            itemsPlanned = 54,
            inputEnabled = true,
            isLoading = false,
        )
}
