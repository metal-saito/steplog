package com.cellomsai.steplog.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellomsai.steplog.data.healthconnect.HealthConnectManager
import com.cellomsai.steplog.data.preferences.UserPreferences
import com.cellomsai.steplog.data.repository.DailyRecordRepository
import com.cellomsai.steplog.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val weatherApiKey: String = "",
    val message: String? = null,
    val csvFile: File? = null,
    val healthConnectAvailable: Boolean = false,
    val healthConnectConnected: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DailyRecordRepository,
    private val userPreferences: UserPreferences,
    val healthConnect: HealthConnectManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.appTheme.collect { theme ->
                _uiState.update { it.copy(appTheme = theme) }
            }
        }
        viewModelScope.launch {
            userPreferences.weatherApiKey.collect { key ->
                _uiState.update { it.copy(weatherApiKey = key) }
            }
        }
        refreshHealthConnectState()
    }

    fun refreshHealthConnectState() {
        val available = healthConnect.isAvailable()
        _uiState.update { it.copy(healthConnectAvailable = available) }
        if (!available) return
        viewModelScope.launch {
            val connected = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
            _uiState.update { it.copy(healthConnectConnected = connected) }
        }
    }

    fun onHealthConnectPermissionsResult() {
        viewModelScope.launch {
            val connected = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    healthConnectConnected = connected,
                    message = if (connected) "Health Connect と連携しました" else "連携が許可されませんでした",
                )
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { userPreferences.setAppTheme(theme) }
    }

    fun onApiKeyChange(key: String) {
        _uiState.update { it.copy(weatherApiKey = key) }
    }

    fun saveApiKey() {
        viewModelScope.launch {
            userPreferences.setWeatherApiKey(_uiState.value.weatherApiKey)
            _uiState.update { it.copy(message = "APIキーを保存しました") }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            runCatching {
                val records = repository.getAllForExport()
                val csv = buildString {
                    appendLine("date,steps,dizziness_level,fatigue_level,sleep_hours,pressure,memo")
                    records.forEach { r ->
                        appendLine(
                            "${r.date},${r.steps},${r.dizzinessLevel ?: ""},${r.fatigueLevel ?: ""}," +
                                "${r.sleepHours ?: ""},${r.pressure ?: ""}," +
                                "\"${(r.memo ?: "").replace("\"", "\"\"")}\""
                        )
                    }
                }
                // キャッシュディレクトリに書き出して共有シートで渡す
                val file = File(context.cacheDir, "steplog_${LocalDate.now()}.csv")
                file.writeText(csv)
                file
            }.onSuccess { file ->
                _uiState.update { it.copy(csvFile = file) }
            }.onFailure {
                _uiState.update { it.copy(message = "エクスポートに失敗しました") }
            }
        }
    }

    fun onCsvShared() {
        _uiState.update { it.copy(csvFile = null) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            runCatching { repository.deleteAll() }
                .onSuccess { _uiState.update { it.copy(message = "データを削除しました") } }
                .onFailure { _uiState.update { it.copy(message = "削除に失敗しました") } }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
