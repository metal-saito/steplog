package com.cellomsai.steplog.ui.screens.calendar

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

data class CalendarUiState(
    val displayMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val records: Map<String, DailyRecord> = emptyMap(),
    val isLoading: Boolean = false,
    val initialSyncDone: Boolean = false,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: DailyRecordRepository,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        val today = LocalDate.now()
        val from = today.minusDays(30)
        viewModelScope.launch {
            repository.observeRange(from, today).collect { list ->
                _uiState.update { it.copy(records = list.associateBy { r -> r.date }) }
            }
        }
        initialSync()
    }

    private fun initialSync() {
        viewModelScope.launch {
            if (!healthConnect.isAvailable() || !healthConnect.hasPermissions()) return@launch
            _uiState.update { it.copy(isLoading = true) }
            val today = LocalDate.now()
            val from = today.minusDays(29)
            runCatching { healthConnect.readStepsForRange(from, today) }
                .onSuccess { stepsMap ->
                    stepsMap.forEach { (date, steps) ->
                        repository.saveSteps(date, steps)
                    }
                }
            _uiState.update { it.copy(isLoading = false, initialSyncDone = true) }
        }
    }

    fun goToPreviousMonth() {
        _uiState.update { it.copy(displayMonth = it.displayMonth.minusMonths(1)) }
    }

    fun goToNextMonth() {
        val next = _uiState.value.displayMonth.plusMonths(1)
        if (!next.isAfter(LocalDate.now().withDayOfMonth(1))) {
            _uiState.update { it.copy(displayMonth = next) }
        }
    }
}
