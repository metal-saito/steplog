package com.cellomsai.steplog.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cellomsai.steplog.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

private const val DEFAULT_WEATHER_API_KEY = "a27dcc491ad3f394cd83b84b910a2931"

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
        val STEP_BASELINE_DATE = stringPreferencesKey("step_baseline_date")
        val STEP_BASELINE_COUNT = longPreferencesKey("step_baseline_count")
    }

    val appTheme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME]) {
            "LIGHT" -> AppTheme.LIGHT
            "DARK" -> AppTheme.DARK
            else -> AppTheme.SYSTEM
        }
    }

    val weatherApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEATHER_API_KEY] ?: DEFAULT_WEATHER_API_KEY
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setWeatherApiKey(key: String) {
        context.dataStore.edit { it[Keys.WEATHER_API_KEY] = key }
    }

    suspend fun getStepBaseline(): Pair<String, Long> {
        val prefs = context.dataStore.data.first()
        val date = prefs[Keys.STEP_BASELINE_DATE] ?: ""
        val count = prefs[Keys.STEP_BASELINE_COUNT] ?: 0L
        return Pair(date, count)
    }

    suspend fun setStepBaseline(date: String, count: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STEP_BASELINE_DATE] = date
            prefs[Keys.STEP_BASELINE_COUNT] = count
        }
    }
}
