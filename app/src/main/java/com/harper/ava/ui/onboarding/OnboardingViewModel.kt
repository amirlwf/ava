package com.harper.ava.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.data.preferences.UserPreferences
import kotlinx.coroutines.launch

/** First-run onboarding (stage 6, spec §14): marks itself seen exactly once. */
class OnboardingViewModel(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    /** Persists "seen", then runs [onFinished] (navigation) — in that order. */
    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            userPreferences.setOnboardingSeen()
            onFinished()
        }
    }

    companion object {
        fun factory(prefs: UserPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OnboardingViewModel(prefs) as T
            }
    }
}
