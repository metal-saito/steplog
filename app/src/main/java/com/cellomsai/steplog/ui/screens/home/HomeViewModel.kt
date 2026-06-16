package com.cellomsai.steplog.ui.screens.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellomsai.steplog.data.database.entity.DailyRecord
import com.cellomsai.steplog.data.healthconnect.HealthConnectManager
import com.cellomsai.steplog.data.repository.DailyRecordRepository
import com.cellomsai.steplog.data.sensor.StepSensorManager
import com.cellomsai.steplog.data.weather.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val record: DailyRecord? = null,
    val steps: Int = 0,
    val isSaving: Boolean = false,
    val isLoadingSteps: Boolean = false,
    val healthConnectAvailable: Boolean = true,
    val healthConnectPermissionGranted: Boolean = false,
    val savedToastVisible: Boolean = false,
    val errorMessage: String? = null,
    val activityRecognitionGranted: Boolean = false,
    val sensorAvailable: Boolean = false,
    val shouldRequestHealthConnect: Boolean = false,
    // HC から実際に読み取った歩数。null=未読 or 読み取り失敗、0以上=実際の値
    val lastHcSteps: Int? = null,
    // 前日の気圧（トレンド表示用）
    val yesterdayPressure: Float? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DailyRecordRepository,
    val healthConnect: HealthConnectManager,
    private val stepSensorManager: StepSensorManager,
    private val weatherRepository: WeatherRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val today = LocalDate.now()
        viewModelScope.launch {
            repository.observeByDate(today).collect { record ->
                _uiState.update {
                    // 当日の歩数は減らさない（保存済み値とライブ値の大きい方を表示）。
                    // 体調・体重などを記録しても歩数表示が巻き戻らないようにする。
                    val steps = maxOf(record?.steps ?: 0, it.steps)
                    it.copy(record = record, steps = steps)
                }
            }
        }

        val sensorAvailable = stepSensorManager.isAvailable
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val hcAvailable = healthConnect.isAvailable()
        _uiState.update {
            it.copy(
                healthConnectAvailable = hcAvailable,
                sensorAvailable = sensorAvailable,
                activityRecognitionGranted = activityRecognitionGranted,
            )
        }

        viewModelScope.launch {
            syncPermissionState()
            // 初回起動時など未連携なら Health Connect の権限リクエストを促す
            val s = _uiState.value
            if (s.healthConnectAvailable && !s.healthConnectPermissionGranted) {
                _uiState.update { it.copy(shouldRequestHealthConnect = true) }
            }
            refreshSteps()
        }
        viewModelScope.launch { fetchWeatherIfNeeded(today) }
        viewModelScope.launch {
            val yesterday = today.minusDays(1)
            val yesterdayRecord = repository.observeByDate(yesterday).first()
            _uiState.update { it.copy(yesterdayPressure = yesterdayRecord?.pressure) }
        }
    }

    // ON_RESUME から呼ばれる：権限状態を再確認して歩数・気圧を更新
    fun onResume() {
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(activityRecognitionGranted = activityRecognitionGranted) }
        viewModelScope.launch {
            syncPermissionState()
            refreshSteps()
        }
        viewModelScope.launch { fetchWeatherIfNeeded(LocalDate.now()) }
    }

    // HC 権限状態を確認する（失敗しても refreshSteps は止めない）
    private suspend fun syncPermissionState() {
        if (!_uiState.value.healthConnectAvailable) return
        val hasPerms = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
        _uiState.update { it.copy(healthConnectPermissionGranted = hasPerms) }
    }

    // 当日の気圧がまだ未取得のときだけ API を叩く
    private suspend fun fetchWeatherIfNeeded(today: LocalDate) {
        if (_uiState.value.record?.pressure != null) return
        val weather = weatherRepository.fetchWeather() ?: return
        runCatching {
            repository.saveWeather(
                date = today,
                pressure = weather.pressure,
                precipitationMm = weather.precipitationMm,
                weatherCode = weather.weatherCode,
            )
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            val hasPerms = healthConnect.hasPermissions()
            _uiState.update { it.copy(healthConnectPermissionGranted = hasPerms) }
            if (hasPerms) refreshSteps()
        }
    }

    fun onActivityRecognitionResult(granted: Boolean) {
        _uiState.update { it.copy(activityRecognitionGranted = granted) }
        refreshSteps()
    }

    // Health Connect の権限リクエストを起動したら呼ぶ（多重起動防止）
    fun onHealthConnectRequestConsumed() {
        _uiState.update { it.copy(shouldRequestHealthConnect = false) }
    }

    fun onLocationPermissionGranted() {
        viewModelScope.launch { fetchWeatherIfNeeded(LocalDate.now()) }
    }

    /**
     * 当日歩数を再取得して保存する。
     * @param showLoading 引っ張り更新などユーザー操作時は true。定期ポーリング時は false にして
     *   スピナーの点滅を防ぐ（DB 経由で歩数だけ静かに更新される）。
     */
    fun refreshSteps(showLoading: Boolean = true) {
        val today = LocalDate.now()
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoadingSteps = true, errorMessage = null) }
                // 権限状態を最新化してから判定（起動直後の競合状態を防ぐ）。
                // 定期ポーリング時は init/onResume で同期済みの状態を流用する。
                syncPermissionState()
            }
            val useSensor = _uiState.value.activityRecognitionGranted && _uiState.value.sensorAvailable
            // HC は他アプリ（OPPOヘルス等）の集計も含むが反映が遅れることがある。
            // センサーがあればそれを起点（seed）にしてリアルタイムに差分加算する。
            val hcSteps: Int? = if (_uiState.value.healthConnectPermissionGranted) {
                runCatching { healthConnect.readSteps(today) }.getOrNull()
            } else null
            // HC の実際の値を UI に公開（0 = HC に歩数なし、null = 未接続 or 読み取り失敗）
            if (_uiState.value.healthConnectPermissionGranted) {
                _uiState.update { it.copy(lastHcSteps = hcSteps) }
            }
            // 読み取り失敗時は null。0 で DB を上書きしてデータを失わないようにする
            val steps: Int? = when {
                useSensor ->
                    runCatching { stepSensorManager.readTodaySteps(seedSteps = hcSteps ?: 0) }.getOrNull()
                _uiState.value.healthConnectPermissionGranted -> hcSteps
                else -> {
                    if (showLoading) _uiState.update { it.copy(isLoadingSteps = false) }
                    return@launch
                }
            }
            if (steps != null) {
                // ライブ読み取り結果を即時に画面へ反映（DB 反映の往復を待たない）。
                // 記録操作後・ポーリング中も当日歩数が常に最新になる。当日値は減らさない。
                _uiState.update { it.copy(steps = maxOf(it.steps, steps)) }
                // 当日分のみ保存。過去日は touch しない（捏造防止）。
                runCatching { repository.saveSteps(today, steps) }
            }
            if (showLoading) _uiState.update { it.copy(isLoadingSteps = false) }
        }
    }

    fun saveBodyCondition(
        dizzinessLevel: Int?,
        fatigueLevel: Int?,
        sleepHours: Float?,
        weightKg: Float?,
        memo: String?,
    ) {
        val today = LocalDate.now()
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                repository.saveBodyCondition(today, dizzinessLevel, fatigueLevel, sleepHours, weightKg, memo)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, savedToastVisible = true) }
            }.onFailure {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "記録の保存ができませんでした")
                }
            }
        }
    }

    fun dismissToast() {
        _uiState.update { it.copy(savedToastVisible = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
