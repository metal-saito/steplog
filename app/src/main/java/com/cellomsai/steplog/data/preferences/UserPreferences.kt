package com.cellomsai.steplog.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cellomsai.steplog.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

// 気圧取得用の API キーはアプリ内蔵。設定画面には表示せず、ユーザーからは変更できない。
private const val BUILTIN_WEATHER_API_KEY = "a27dcc491ad3f394cd83b84b910a2931"

/**
 * 歩数センサー（TYPE_STEP_COUNTER）の蓄積状態。
 * @param date 計測中の日付（yyyy-MM-dd）
 * @param lastSensor 最後に処理した累計センサー値（再起動検知用）
 * @param dailyTotal 当日のここまでの累計歩数（表示・DB保存の元値）
 */
data class StepState(
    val date: String,
    val lastSensor: Long,
    val dailyTotal: Int,
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val STEP_DATE = stringPreferencesKey("step_date")
        val STEP_LAST_SENSOR = longPreferencesKey("step_last_sensor")
        val STEP_DAILY_TOTAL = intPreferencesKey("step_daily_total")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val appTheme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME]) {
            "LIGHT" -> AppTheme.LIGHT
            "DARK" -> AppTheme.DARK
            else -> AppTheme.SYSTEM
        }
    }

    // 内蔵キーを返すのみ（DataStore には保存しない・変更不可）
    val weatherApiKey: Flow<String> = flowOf(BUILTIN_WEATHER_API_KEY)

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun getStepState(): StepState {
        val prefs = context.dataStore.data.first()
        return StepState(
            date = prefs[Keys.STEP_DATE] ?: "",
            lastSensor = prefs[Keys.STEP_LAST_SENSOR] ?: 0L,
            dailyTotal = prefs[Keys.STEP_DAILY_TOTAL] ?: 0,
        )
    }

    suspend fun setStepState(date: String, lastSensor: Long, dailyTotal: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STEP_DATE] = date
            prefs[Keys.STEP_LAST_SENSOR] = lastSensor
            prefs[Keys.STEP_DAILY_TOTAL] = dailyTotal
        }
    }
}
