package com.cellomsai.steplog.ui.screens.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellomsai.steplog.data.database.entity.DailyRecord
import com.cellomsai.steplog.data.repository.DailyRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

enum class GraphRange(val days: Int, val label: String) {
    WEEK(7, "7日"),
    MONTH(30, "30日"),
    THREE_MONTHS(90, "90日"),
}

data class GraphUiState(
    val range: GraphRange = GraphRange.MONTH,
    val records: List<DailyRecord> = emptyList(),
) {
    val averageSteps: Int
        get() = records.filter { it.steps > 0 }.let {
            if (it.isEmpty()) 0 else it.sumOf { r -> r.steps } / it.size
        }

    val recordedDays: Int
        get() = records.count { it.dizzinessLevel != null || it.fatigueLevel != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GraphViewModel @Inject constructor(
    private val repository: DailyRecordRepository,
) : ViewModel() {

    private val _range = MutableStateFlow(GraphRange.MONTH)

    val uiState: StateFlow<GraphUiState> = _range.flatMapLatest { range ->
        val today = LocalDate.now()
        val from = today.minusDays(range.days.toLong() - 1)
        repository.observeRange(from, today).map { records ->
            GraphUiState(range = range, records = records.sortedBy { it.date })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GraphUiState(),
    )

    fun setRange(range: GraphRange) {
        _range.value = range
    }
}
