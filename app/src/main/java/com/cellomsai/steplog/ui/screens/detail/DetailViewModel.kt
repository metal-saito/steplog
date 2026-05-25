package com.cellomsai.steplog.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellomsai.steplog.data.database.entity.DailyRecord
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
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: DailyRecordRepository,
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
    }

    fun save(dizziness: Int?, fatigue: Int?, sleepHours: Float?, weightKg: Float?, memo: String?) {
        val date = currentDate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                repository.saveBodyCondition(date, dizziness, fatigue, sleepHours, weightKg, memo)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, savedToastVisible = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "記録の保存ができませんでした") }
            }
        }
    }

    fun dismissToast() = _uiState.update { it.copy(savedToastVisible = false) }
}
