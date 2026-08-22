package com.tonic.app.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug-only escape from the intake flow — onboarding and the M0 diagnostic.
 *
 * `:feature:settings`' jump tool already clears both, but a tester could never reach it: Settings
 * opens from Home, and on a fresh install Home immediately navigates to onboarding and then to the
 * diagnostic, popping itself off the back stack each time. The skip existed behind the very screen the
 * diagnostic was blocking, so reinstalling to escape a bad state meant sitting through the diagnostic
 * again regardless — which is exactly what happened to the tester who asked for this.
 *
 * Lives in `:app` because `:app` owns navigation and knows the build type; `:feature:diagnostic` needs
 * neither, and is handed a nullable callback instead (null in a release build, so the control is not
 * merely hidden but absent).
 */
@HiltViewModel
class DebugIntakeViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Marks onboarding and the diagnostic done. [onSkipped] runs once the writes have landed. */
        fun skipIntake(onSkipped: () -> Unit) {
            viewModelScope.launch {
                settingsRepository.setOnboardingCompleted(true)
                settingsRepository.setDiagnosticCompleted(true)
                onSkipped()
            }
        }
    }
