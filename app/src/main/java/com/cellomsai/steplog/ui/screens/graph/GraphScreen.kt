package com.cellomsai.steplog.ui.screens.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellomsai.steplog.ui.theme.ConditionColors
import java.text.NumberFormat
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(viewModel: GraphViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("グラフ") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 期間セレクター
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GraphRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = uiState.range == range,
                        onClick = { viewModel.setRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = GraphRange.entries.size),
                        label = { Text(range.label) },
                    )
                }
            }

            // サマリー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    label = "平均歩数",
                    value = "${NumberFormat.getNumberInstance().format(uiState.averageSteps)} 歩",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "体調記録日数",
                    value = "${uiState.recordedDays} 日",
                    modifier = Modifier.weight(1f),
                )
            }

            if (uiState.records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "データがありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 歩数グラフ
                ChartCard(title = "歩数") {
                    val barColor = MaterialTheme.colorScheme.primary
                    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
                    StepsBarChart(
                        steps = uiState.records.map { it.steps },
                        barColor = barColor,
                        emptyColor = emptyColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    )
                }

                // 体重グラフ
                val today = remember { LocalDate.now() }
                val allDates = remember(uiState.range) {
                    (0 until uiState.range.days).map {
                        today.minusDays((uiState.range.days - 1 - it).toLong())
                    }
                }
                val recordsByDate = remember(uiState.records) {
                    uiState.records.associateBy { it.date }
                }
                val weightPoints = remember(allDates, recordsByDate) {
                    allDates.mapIndexedNotNull { i, date ->
                        val w = recordsByDate[date.toString()]?.weightKg
                        if (w != null) i to w else null
                    }
                }

                ChartCard(title = "体重（kg）") {
                    if (weightPoints.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "体重データなし",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val lineColor = MaterialTheme.colorScheme.tertiary
                        val dotColor = MaterialTheme.colorScheme.tertiary
                        WeightLineChart(
                            points = weightPoints,
                            totalDays = uiState.range.days,
                            lineColor = lineColor,
                            dotColor = dotColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        )
                    }
                }

                // 体調グラフ
                ChartCard(title = "めまい度・疲労度") {
                    ConditionChart(
                        dizziness = uiState.records.map { it.dizzinessLevel },
                        fatigue = uiState.records.map { it.fatigueLevel },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StepsBarChart(
    steps: List<Int>,
    barColor: Color,
    emptyColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (steps.isEmpty()) return@Canvas
        val maxSteps = steps.max().coerceAtLeast(1)
        val n = steps.size
        val slotWidth = size.width / n
        val barWidth = (slotWidth * 0.6f).coerceAtLeast(2f)

        steps.forEachIndexed { i, s ->
            val barHeight = if (s > 0) (s.toFloat() / maxSteps) * size.height else 2f
            val left = i * slotWidth + (slotWidth - barWidth) / 2f
            drawRect(
                color = if (s > 0) barColor.copy(alpha = 0.75f) else emptyColor,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

/**
 * Line chart for weight data. Draws segments between consecutive non-null points,
 * skipping gaps where no data was recorded.
 *
 * @param points list of (dateIndex, weightKg) for days that have weight data
 * @param totalDays total number of days in the displayed range (x-axis span)
 */
@Composable
private fun WeightLineChart(
    points: List<Pair<Int, Float>>,
    totalDays: Int,
    lineColor: Color,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val minWeight = points.minOf { it.second }
        val maxWeight = points.maxOf { it.second }
        val weightRange = (maxWeight - minWeight).coerceAtLeast(1f)

        fun xOf(index: Int) = if (totalDays <= 1) size.width / 2f
            else (index.toFloat() / (totalDays - 1)) * size.width

        fun yOf(weight: Float) = size.height - ((weight - minWeight) / weightRange) * size.height * 0.85f - size.height * 0.075f

        // draw line segments between consecutive recorded points
        for (i in 0 until points.size - 1) {
            val (idxA, wA) = points[i]
            val (idxB, wB) = points[i + 1]
            drawLine(
                color = lineColor.copy(alpha = 0.8f),
                start = Offset(xOf(idxA), yOf(wA)),
                end = Offset(xOf(idxB), yOf(wB)),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // draw dots at each data point
        val dotRadius = 4.dp.toPx()
        points.forEach { (idx, w) ->
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(xOf(idx), yOf(w)),
            )
        }
    }
}

@Composable
private fun ConditionChart(
    dizziness: List<Int?>,
    fatigue: List<Int?>,
    modifier: Modifier = Modifier,
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("めまい" to dizziness, "疲労" to fatigue).forEach { (label, levels) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp),
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    levels.forEach { level ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .background(
                                    color = if (level != null)
                                        ConditionColors.getOrElse(level) { Color.Transparent }
                                    else surfaceVariant,
                                    shape = MaterialTheme.shapes.extraSmall,
                                ),
                        )
                    }
                }
            }
        }
    }
}
