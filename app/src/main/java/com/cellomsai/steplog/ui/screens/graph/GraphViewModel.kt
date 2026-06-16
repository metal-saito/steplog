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
 * 気圧・天気と不調スコアの相関。不調スコア = (めまい度 + 疲労度) / 2 (0=良好, 5=最悪)。
 *
 * - 気圧: 閾値以下を低気圧、以上を高気圧と分類して平均不調スコアを比較
 * - 天気: 降水のあった日（雨）と無かった日で平均不調スコアを比較（[rain] が非 null のときのみ）
 * - [combinedNote]: 「低気圧かつ雨」の日に不調が偏る場合の実用的な一文（条件を満たすときのみ）
 */
data class CorrelationInsight(
    val totalPoints: Int,
    val lowPressureDays: Int,
    val highPressureDays: Int,
    val lowPressureAvgBadness: Float,
    val highPressureAvgBadness: Float,
    val rain: RainStats? = null,
    val combinedNote: String? = null,
    val threshold: Float = 1005f,
) {
    val diff: Float get() = lowPressureAvgBadness - highPressureAvgBadness

    val summaryText: String
        get() {
            val parts = mutableListOf<String>()
            when {
                diff > 0.5f -> parts += "低気圧の日は不調スコアが %.1f 高い傾向".format(diff)
                diff < -0.5f -> parts += "高気圧の日のほうが不調になりやすい傾向"
            }
            rain?.let { r ->
                when {
                    r.diff > 0.5f -> parts += "雨の日は不調スコアが %.1f 高い傾向".format(r.diff)
                    r.diff < -0.5f -> parts += "雨でない日のほうが不調になりやすい傾向"
                }
            }
            return if (parts.isEmpty()) {
                "気圧・天気と体調の明確な相関は今のところ見られません"
            } else {
                parts.joinToString("。") + "。"
            }
        }
}

/** 雨の日 vs 雨でない日の平均不調スコア比較。 */
data class RainStats(
    val rainyDays: Int,
    val dryDays: Int,
    val rainyAvgBadness: Float,
    val dryAvgBadness: Float,
) {
    val diff: Float get() = rainyAvgBadness - dryAvgBadness
}

/** 不調スコア。めまい度・疲労度の両方/片方から算出。どちらも未入力なら null。 */
private fun badnessOf(r: DailyRecord): Float? = when {
    r.dizzinessLevel != null && r.fatigueLevel != null -> (r.dizzinessLevel + r.fatigueLevel) / 2f
    r.dizzinessLevel != null -> r.dizzinessLevel.toFloat()
    r.fatigueLevel != null -> r.fatigueLevel.toFloat()
    else -> null
}

/**
 * その日が「雨（降水あり）」だったか。
 * 天気データが無ければ null（集計対象外）。降水量 > 0、もしくは天気コードが
 * 雷雨(2xx)・霧雨(3xx)・雨(5xx)・雪(6xx)の範囲なら true。
 */
private fun wetnessOf(r: DailyRecord): Boolean? {
    if (r.weatherCode == null && r.precipitationMm == null) return null
    if ((r.precipitationMm ?: 0f) > 0f) return true
    if (r.weatherCode != null && r.weatherCode in 200..699) return true
    return false
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
            // 不調スコアが算出できる日だけを対象に、気圧・雨の有無を併せ持つ点に変換
            data class Point(val pressure: Float?, val badness: Float, val wet: Boolean?)
            val points = records.mapNotNull { r ->
                val badness = badnessOf(r) ?: return@mapNotNull null
                Point(pressure = r.pressure, badness = badness, wet = wetnessOf(r))
            }

            // --- 気圧相関（従来）: 気圧と体調がそろった日が 5 日以上 ---
            val pressurePoints = points.filter { it.pressure != null }
            if (pressurePoints.size < 5) return null
            val low = pressurePoints.filter { it.pressure!! < threshold }
            val high = pressurePoints.filter { it.pressure!! >= threshold }
            if (low.isEmpty() || high.isEmpty()) return null

            // --- 雨相関: 天気と体調がそろった日が 5 日以上、雨/非雨の両方が存在 ---
            val rainPoints = points.filter { it.wet != null }
            val rainStats = if (rainPoints.size >= 5) {
                val rainy = rainPoints.filter { it.wet == true }
                val dry = rainPoints.filter { it.wet == false }
                if (rainy.isEmpty() || dry.isEmpty()) null
                else RainStats(
                    rainyDays = rainy.size,
                    dryDays = dry.size,
                    rainyAvgBadness = rainy.map { it.badness }.average().toFloat(),
                    dryAvgBadness = dry.map { it.badness }.average().toFloat(),
                )
            } else null

            // --- 複合インサイト: 「低気圧かつ雨」の日に不調が偏るか ---
            val combinedNote = run {
                val both = points.filter {
                    it.pressure != null && it.pressure!! < threshold && it.wet == true
                }
                if (both.size < 3) return@run null
                val bothAvg = both.map { it.badness }.average().toFloat()
                val overallAvg = points.map { it.badness }.average().toFloat()
                if (bothAvg - overallAvg > 0.5f) {
                    "特に低気圧かつ雨の日は不調スコア %.1f と高めです（%d日分）".format(bothAvg, both.size)
                } else null
            }

            return CorrelationInsight(
                totalPoints = pressurePoints.size,
                lowPressureDays = low.size,
                highPressureDays = high.size,
                lowPressureAvgBadness = low.map { it.badness }.average().toFloat(),
                highPressureAvgBadness = high.map { it.badness }.average().toFloat(),
                rain = rainStats,
                combinedNote = combinedNote,
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
