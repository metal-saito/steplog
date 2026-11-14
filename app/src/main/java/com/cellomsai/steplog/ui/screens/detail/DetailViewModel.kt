package com.cellomsai.steplog.ui.screens.detail

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
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class DetailUiState(
    val displayDate: String = "",
    val record: DailyRecord? = null,
    val isSaving: Boolean = false,
    val savedToastVisible: Boolean = false,
    val errorMessage: String? = null,
    // Health Connect から取得できたその日の睡眠時間（null = 取得できない → 手入力のまま）
    val healthConnectSleepHours: Float? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: DailyRecordRepository,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var currentDate: LocalDate? = null

    fun load(dateStr: String) {
        val date = LocalDate.parse(dateStr)
        currentDate = date
        _uiState.update {
            it.copy(
                displayDate = date.format(
                    DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.JAPANESE)
                )
            )
        }
        viewModelScope.launch {
            repository.observeByDate(date).collect { record ->
                _uiState.update { it.copy(record = record) }
            }
        }
        // その日の睡眠記録が Health Connect にあれば取得（未入力なら保存）。失敗しても何もしない
        viewModelScope.launch {
            if (date.isAfter(LocalDate.now())) return@launch
            val hours = runCatching { healthConnect.readSleepHours(date) }.getOrNull()
            _uiState.update { it.copy(healthConnectSleepHours = hours) }
            if (hours != null) runCatching { repository.fillSleepHoursIfMissing(date, hours) }
        }
    }

    fun save(
        dizziness: Int?,
        fatigue: Int?,
        tinnitus: Int?,
        headache: Int?,
        sleepHours: Float?,
        weightKg: Float?,
        memo: String?,
    ) {
        val date = currentDate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                repository.saveBodyCondition(date, dizziness, fatigue, tinnitus, headache, sleepHours, weightKg, memo)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, savedToastVisible = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "記録の保存ができませんでした") }
            }
        }
    }

    fun dismissToast() = _uiState.update { it.copy(savedToastVisible = false) }
}
