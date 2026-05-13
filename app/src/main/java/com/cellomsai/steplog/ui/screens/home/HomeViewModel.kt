package com.cellomsai.steplog.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellomsai.steplog.data.database.entity.DailyRecord
import com.cellomsai.steplog.data.healthconnect.HealthConnectManager
import com.cellomsai.steplog.data.repository.DailyRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val launchPermissionRequest: Boolean = false,
    val savedToastVisible: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DailyRecordRepository,
    val healthConnect: HealthConnectManager,
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
        val available = healthConnect.isAvailable()
        _uiState.update { it.copy(healthConnectAvailable = available) }
        if (available) {
            viewModelScope.launch { syncPermissionState() }
        }
    }

    // ON_RESUME から呼ばれる：権限状態を再確認して歩数を更新
    fun onResume() {
        if (!_uiState.value.healthConnectAvailable) return
        viewModelScope.launch {
            syncPermissionState()
        }
    }

    // 権限状態を確認し、付与済みなら歩数を取得
    private suspend fun syncPermissionState() {
        val hasPerms = healthConnect.hasPermissions()
        _uiState.update { it.copy(healthConnectPermissionGranted = hasPerms) }
        if (hasPerms) refreshSteps()
    }

    // カードのボタンから直接 PermissionController を起動する
    fun requestPermissions() {
        _uiState.update { it.copy(launchPermissionRequest = true) }
    }

    fun onPermissionRequestLaunched() {
        _uiState.update { it.copy(launchPermissionRequest = false) }
    }

    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            val hasPerms = healthConnect.hasPermissions()
            _uiState.update { it.copy(healthConnectPermissionGranted = hasPerms) }
            if (hasPerms) refreshSteps()
        }
    }

    fun refreshSteps() {
        val today = LocalDate.now()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSteps = true, errorMessage = null) }
            runCatching { healthConnect.readSteps(today) }
                .onSuccess { steps ->
                    repository.saveSteps(today, steps)
                    _uiState.update { it.copy(isLoadingSteps = false) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoadingSteps = false, errorMessage = "歩数の取得ができませんでした")
                    }
                }
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
