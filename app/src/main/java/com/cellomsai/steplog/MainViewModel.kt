package com.cellomsai.steplog

import androidx.lifecycle.ViewModel
import com.cellomsai.steplog.data.preferences.UserPreferences
import com.cellomsai.steplog.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferences: UserPreferences,
) : ViewModel() {
    val appTheme: Flow<AppTheme> = userPreferences.appTheme
}
