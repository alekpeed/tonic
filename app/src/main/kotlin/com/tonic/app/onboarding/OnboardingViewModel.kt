package com.tonic.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One screen, one job: record that the walkthrough was shown. [com.tonic.app.home.HomeViewModel]
 * reads [com.tonic.core.model.state.AppSettings.onboardingCompleted] to decide whether to route here
 * again - that field already existed in the schema but nothing wrote to it before this.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        fun onDone(then: () -> Unit) {
            viewModelScope.launch {
                settingsRepository.setOnboardingCompleted(true)
                then()
            }
        }
    }
