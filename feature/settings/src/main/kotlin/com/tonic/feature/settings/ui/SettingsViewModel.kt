package com.tonic.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.export.DataExportRepository
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import com.tonic.core.model.time.Clock
import com.tonic.feature.settings.debug.DebugSkillJumper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * docs/04-ARCHITECTURE.md §3: thin by design - every field here is a direct pass-through to
 * [SettingsRepository]'s DataStore-backed [com.tonic.core.model.state.AppSettings]. Unlike Practice or
 * Diagnostic, there is no one-time session to start; continuously collecting [SettingsRepository.settings]
 * is the entire job, so a plain `init` block (rather than an explicit `startIfNeeded()` guard) is enough -
 * it runs exactly once per ViewModel instance, and the instance itself already survives rotation.
 * docs/09-BUILD-PLAN.md Stage 9 acceptance: "settings changes take effect immediately" - every setter
 * below writes through the repository, whose [SettingsRepository.settings] flow is what [uiState] mirrors,
 * so a change is visible here (and to any other collector, like `:feature:practice`'s ViewModel) the
 * moment the DataStore write completes, with no separate "apply" step.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val sessionRepository: SessionRepository,
        private val dataExportRepository: DataExportRepository,
        private val debugSkillJumper: DebugSkillJumper,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                SettingsUiState(
                    // Both tracks - see DebugSkillJumper's KDoc. M3.DOWNBEAT was missing from this list
                    // entirely (every M3 node was, not just that one) because this only ever read the
                    // pitch chain; rhythm has its own, independent chain that this dropped on the floor.
                    debugJumpTargets = (SkillGraph.practiceChain + SkillGraph.rhythmChain).map { it.id },
                ),
            )
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsRepository.settings.collect { settings ->
                    _uiState.update { it.copy(settings = settings, isLoading = false) }
                }
            }
        }

        /**
         * Seeds whatever still blocks [target] so the practice loop lands there next — the fix for "I
         * can't debug it if I can't get through the level." `BuildConfig.DEBUG`-only; see
         * [DebugSkillJumper]'s KDoc for why this does not compromise mastery's meaning for real play.
         */
        fun onDebugJumpRequested(target: SkillId) {
            // A jump takes real time (hundreds of attempts, a mastery replay per node), so the press
            // has to say so immediately or it reads as a dead button - which is exactly how the first
            // version came across.
            if (_uiState.value.debugJumpInProgress) return
            _uiState.update { it.copy(debugJumpInProgress = true, debugJumpResult = null) }
            viewModelScope.launch {
                // Never let this crash the app. The first version had no catch at all, so the
                // unsatisfiable-target bug surfaced to a tester as a hard crash with nothing said
                // about why. A debug tool that fails silently or fatally is worse than no tool.
                val result =
                    runCatching { debugSkillJumper.jumpTo(target) }
                        .fold(
                            onSuccess = { DebugJumpResult(target, it.size, failure = null) },
                            onFailure = { DebugJumpResult(target, seededCount = 0, failure = it.message ?: "failed") },
                        )
                _uiState.update {
                    it.copy(
                        debugJumpInProgress = false,
                        debugJumpResult = result,
                        debugJumpNavigateTo = target.takeIf { _ -> result.failure == null },
                    )
                }
            }
        }

        /**
         * Clears the result once the screen has navigated on it, so returning to Settings does not
         * immediately bounce back out on a stale success.
         */
        fun onDebugJumpNavigationHandled() {
            _uiState.update { it.copy(debugJumpNavigateTo = null) }
        }

        /**
         * Clears the in-progress/resumable session and nothing else - see
         * [SessionRepository.discardResumable] for exactly what is (and is not) touched. The escape
         * hatch for a session saved under a since-fixed bug: without it, the resume offer keeps
         * restoring the broken state across an update.
         */
        fun onDiscardSavedSession() {
            viewModelScope.launch {
                val discarded = sessionRepository.discardResumable(clock.now())
                _uiState.update {
                    it.copy(discardResult = if (discarded) DiscardResult.DISCARDED else DiscardResult.NOTHING_SAVED)
                }
            }
        }

        /**
         * Builds the export document and hands it to the screen to be saved — docs/20-PHASE-2-SPEC.md
         * §6. Nothing leaves the device here and nothing can: the app has no network permission at all
         * (docs/01-PRODUCT-SPEC.md §4). Where the file goes is entirely the user's choice, made in the
         * system picker, and the app never learns the destination beyond whether the write succeeded.
         */
        fun onExportDataRequested() {
            viewModelScope.launch {
                val json = dataExportRepository.exportToJson()
                val name = dataExportRepository.suggestedFileName()
                _uiState.update {
                    it.copy(pendingExport = PendingExport(name, json), exportResult = null)
                }
            }
        }

        /** The screen has finished with (or abandoned) the picker. Clears the payload either way. */
        fun onExportFinished(result: ExportResult) {
            _uiState.update { it.copy(pendingExport = null, exportResult = result) }
        }

        fun onLabelStyleChanged(style: LabelStyle) {
            viewModelScope.launch { settingsRepository.setLabelStyle(style) }
        }

        fun onReferenceA4HzChanged(hz: Float) {
            viewModelScope.launch { settingsRepository.setReferenceA4Hz(hz) }
        }

        fun onSessionLengthMinutesChanged(minutes: Int) {
            viewModelScope.launch { settingsRepository.setSessionLengthMinutes(minutes) }
        }

        fun onHapticsEnabledChanged(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
        }

        fun onSoundEffectsEnabledChanged(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setSoundEffectsEnabled(enabled) }
        }

        /** docs/40-PHASE-4-SPEC.md §7.2's optional toggle. */
        fun onAudibleTapsEnabledChanged(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setAudibleTapsEnabled(enabled) }
        }

        fun onThemeModeChanged(mode: ThemeMode) {
            viewModelScope.launch { settingsRepository.setThemeMode(mode) }
        }

        fun onReduceMotionChanged(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setReduceMotion(enabled) }
        }

        /**
         * Turns the sung response on or off — docs/30-PHASE-3-SPEC.md §6.1.
         *
         * Only ever called with `true` once microphone permission has actually been granted; the
         * permission request and its explanation live in [SungResponseSection], because they need a
         * composition to launch from. This setter is deliberately unaware of that: a permission the
         * app does not hold is caught again at the point of use by `MicrophoneSource.isAvailable`,
         * which is checked live, so a stale `true` here disables singing rather than breaking it.
         */
        fun onSungResponseEnabledChanged(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setSungResponseEnabled(enabled) }
        }

        fun onSungOctaveAgnosticChanged(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setSungOctaveAgnostic(enabled) }
        }

        /**
         * [enabled]/[time] must agree - docs/05-DATA-MODEL.md §3 - which is why this is one setter, not
         * two: a composable flipping the toggle on picks a default time in the same call, and flipping it
         * off always clears the time, so the two never drift out of sync.
         */
        fun onDailyReminderChanged(
            enabled: Boolean,
            time: String?,
        ) {
            viewModelScope.launch { settingsRepository.setDailyReminder(enabled, if (enabled) time else null) }
        }
    }
