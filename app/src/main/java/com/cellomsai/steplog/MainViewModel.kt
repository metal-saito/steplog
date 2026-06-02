package com.cellomsai.steplog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellomsai.steplog.data.preferences.UserPreferences
import com.cellomsai.steplog.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    val appTheme: Flow<AppTheme> = userPreferences.appTheme

    // null = DataStore still loading; false = first launch; true = already completed
    val onboardingDone: StateFlow<Boolean?> = userPreferences.onboardingDone
        .map { it as Boolean? }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    fun completeOnboarding() {
        viewModelScope.launch { userPreferences.setOnboardingDone() }
    }
}
