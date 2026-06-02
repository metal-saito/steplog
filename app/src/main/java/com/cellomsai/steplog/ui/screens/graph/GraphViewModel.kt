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

/**
 * 気圧と不調スコアの相関。不調スコア = (めまい度 + 疲労度) / 2 (0=良好, 5=最悪)。
 * 閾値以下を低気圧、以上を高気圧と分類して平均不調スコアを比較する。
 */
data class CorrelationInsight(
    val totalPoints: Int,
    val lowPressureDays: Int,
    val highPressureDays: Int,
    val lowPressureAvgBadness: Float,
    val highPressureAvgBadness: Float,
    val threshold: Float = 1005f,
) {
    val diff: Float get() = lowPressureAvgBadness - highPressureAvgBadness

    val summaryText: String
        get() = when {
            diff > 0.5f -> "低気圧の日は不調スコアが %.1f 高い傾向があります".format(diff)
            diff < -0.5f -> "高気圧の日のほうが不調になりやすい傾向があります"
            else -> "気圧と体調の明確な相関は今のところ見られません"
        }
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

    val correlationInsight: CorrelationInsight?
        get() {
            val threshold = 1005f
            val points = records.mapNotNull { r ->
                val pressure = r.pressure ?: return@mapNotNull null
                val badness = when {
                    r.dizzinessLevel != null && r.fatigueLevel != null ->
                        (r.dizzinessLevel + r.fatigueLevel) / 2f
                    r.dizzinessLevel != null -> r.dizzinessLevel.toFloat()
                    r.fatigueLevel != null -> r.fatigueLevel.toFloat()
                    else -> return@mapNotNull null
                }
                pressure to badness
            }
            if (points.size < 5) return null
            val low = points.filter { it.first < threshold }
            val high = points.filter { it.first >= threshold }
            if (low.isEmpty() || high.isEmpty()) return null
            return CorrelationInsight(
                totalPoints = points.size,
                lowPressureDays = low.size,
                highPressureDays = high.size,
                lowPressureAvgBadness = low.map { it.second }.average().toFloat(),
                highPressureAvgBadness = high.map { it.second }.average().toFloat(),
            )
        }
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
