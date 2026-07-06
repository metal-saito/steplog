package com.cellomsai.steplog.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellomsai.steplog.data.backup.BackupData
import com.cellomsai.steplog.data.healthconnect.HealthConnectManager
import com.cellomsai.steplog.data.preferences.UserPreferences
import com.cellomsai.steplog.data.repository.DailyRecordRepository
import com.cellomsai.steplog.ui.theme.AppTheme
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val message: String? = null,
    val csvFile: File? = null,
    val backupFile: File? = null,
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

    fun exportCsv() {
        viewModelScope.launch {
            runCatching {
                val records = repository.getAllForExport()
                val csv = buildString {
                    appendLine(
                        "date,steps,dizziness_level,fatigue_level,tinnitus_level,sleep_hours," +
                            "pressure,precipitation_mm,weather_code,weight_kg,memo"
                    )
                    records.forEach { r ->
                        appendLine(
                            "${r.date},${r.steps},${r.dizzinessLevel ?: ""},${r.fatigueLevel ?: ""}," +
                                "${r.tinnitusLevel ?: ""},${r.sleepHours ?: ""},${r.pressure ?: ""}," +
                                "${r.precipitationMm ?: ""},${r.weatherCode ?: ""},${r.weightKg ?: ""}," +
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

    /** 機種変更用: 全記録を JSON バックアップに書き出し、共有シートで渡す。 */
    fun exportBackup() {
        viewModelScope.launch {
            runCatching {
                val records = repository.getAllForExport()
                val backup = BackupData(
                    exportedAt = System.currentTimeMillis(),
                    records = records,
                )
                val json = Gson().toJson(backup)
                val file = File(context.cacheDir, "steplog_backup_${LocalDate.now()}.json")
                file.writeText(json)
                file
            }.onSuccess { file ->
                _uiState.update { it.copy(backupFile = file) }
            }.onFailure {
                _uiState.update { it.copy(message = "バックアップの書き出しに失敗しました") }
            }
        }
    }

    fun onBackupShared() {
        _uiState.update { it.copy(backupFile = null) }
    }

    /**
     * 機種変更用: 選択された JSON バックアップを読み込んで復元する。
     * 同一日付の記録はバックアップ側で上書きされる（マージ）。
     */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("ファイルを開けません")
                }
                val backup = Gson().fromJson(json, BackupData::class.java)
                    ?: error("バックアップを解析できません")
                val records = backup.records
                require(records.isNotEmpty()) { "復元できる記録がありません" }
                repository.upsertAll(records)
                records.size
            }
            result
                .onSuccess { count ->
                    _uiState.update { it.copy(message = "${count}件の記録を復元しました") }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(message = "復元に失敗しました。正しいバックアップファイルか確認してください。")
                    }
                }
        }
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
