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
                _uiState.update { it.copy(record = record, steps = record?.steps ?: 0) }
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
            if (activityRecognitionGranted && sensorAvailable) refreshSteps()
        }
        viewModelScope.launch { fetchWeatherIfNeeded(today) }
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
        val pressure = weatherRepository.fetchPressure() ?: return
        runCatching { repository.savePressure(today, pressure) }
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

    fun onLocationPermissionGranted() {
        viewModelScope.launch { fetchWeatherIfNeeded(LocalDate.now()) }
    }

    fun refreshSteps() {
        val today = LocalDate.now()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSteps = true, errorMessage = null) }
            val steps = when {
                _uiState.value.activityRecognitionGranted && _uiState.value.sensorAvailable ->
                    runCatching { stepSensorManager.readTodaySteps() }.getOrDefault(0)
                _uiState.value.healthConnectPermissionGranted ->
                    runCatching { healthConnect.readSteps(today) }.getOrDefault(0)
                else -> {
                    _uiState.update { it.copy(isLoadingSteps = false) }
                    return@launch
                }
            }
            runCatching { repository.saveSteps(today, steps) }
            _uiState.update { it.copy(isLoadingSteps = false) }
        }
    }

    fun saveBodyCondition(
        dizzinessLevel: Int?,
        fatigueLevel: Int?,
        sleepHours: Float?,
        memo: String?,
    ) {
        val today = LocalDate.now()
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                repository.saveBodyCondition(today, dizzinessLevel, fatigueLevel, sleepHours, memo)
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
