package com.cellomsai.steplog.ui.screens.graph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
                // 気圧×体調 相関インサイト（最重要カード）
                CorrelationInsightCard(insight = uiState.correlationInsight)

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
                        EmptyChartPlaceholder("体重データなし")
                    } else {
                        val color = MaterialTheme.colorScheme.tertiary
                        LineChart(
                            points = weightPoints,
                            totalDays = uiState.range.days,
                            lineColor = color,
                            dotColor = color,
                            unitFormat = { "%.1f".format(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        )
                    }
                }

                // 気圧グラフ
                val pressurePoints = remember(allDates, recordsByDate) {
                    allDates.mapIndexedNotNull { i, date ->
                        val p = recordsByDate[date.toString()]?.pressure
                        if (p != null) i to p else null
                    }
                }

                ChartCard(title = "気圧（hPa）") {
                    if (pressurePoints.isEmpty()) {
                        EmptyChartPlaceholder("気圧データなし")
                    } else {
                        val color = MaterialTheme.colorScheme.secondary
                        LineChart(
                            points = pressurePoints,
                            totalDays = uiState.range.days,
                            lineColor = color,
                            dotColor = color,
                            unitFormat = { "%.0f".format(it) },
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
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(steps) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier) {
        if (steps.isEmpty()) return@Canvas
        val p = animProgress.value
        val maxSteps = steps.max().coerceAtLeast(1)
        val n = steps.size
        val slotWidth = size.width / n
        val barWidth = (slotWidth * 0.6f).coerceAtLeast(2f)

        steps.forEachIndexed { i, s ->
            val fullHeight = if (s > 0) (s.toFloat() / maxSteps) * size.height else 2f
            val barHeight = if (s > 0) (fullHeight * p).coerceAtLeast(0f) else 2f
            val left = i * slotWidth + (slotWidth - barWidth) / 2f
            drawRect(
                color = if (s > 0) barColor.copy(alpha = 0.75f) else emptyColor,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

@Composable
private fun EmptyChartPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 折れ線グラフ（体重・気圧など連続値用）。記録のある日同士だけを直線でつなぎ、
 * 入力のない日（欠損）はスキップする。
 *
 * @param points 値のある日の (日付インデックス, 値) のリスト
 * @param totalDays 表示期間の日数（X 軸スパン）
 * @param unitFormat 最大値・最小値ラベルの整形関数
 */
@Composable
private fun LineChart(
    points: List<Pair<Int, Float>>,
    totalDays: Int,
    lineColor: Color,
    dotColor: Color,
    unitFormat: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    val minValue = points.minOf { it.second }
    val maxValue = points.maxOf { it.second }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        revealProgress.snapTo(0f)
        revealProgress.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
    }

    Row(modifier = modifier) {
        // 左側に最大値・最小値の目盛りラベル（読みやすさ向上）
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = unitFormat(maxValue),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(
                text = unitFormat(minValue),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val range = (maxValue - minValue).coerceAtLeast(1f)
            val clipWidth = size.width * revealProgress.value

            fun xOf(index: Int) = if (totalDays <= 1) size.width / 2f
                else (index.toFloat() / (totalDays - 1)) * size.width

            fun yOf(value: Float) =
                size.height - ((value - minValue) / range) * size.height * 0.85f - size.height * 0.075f

            // 記録のある点同士だけを線でつなぐ（欠損日はまたいでつなぐ）
            clipRect(right = clipWidth) {
                for (i in 0 until points.size - 1) {
                    val (idxA, vA) = points[i]
                    val (idxB, vB) = points[i + 1]
                    drawLine(
                        color = lineColor.copy(alpha = 0.8f),
                        start = Offset(xOf(idxA), yOf(vA)),
                        end = Offset(xOf(idxB), yOf(vB)),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            val dotRadius = 4.dp.toPx()
            points.forEach { (idx, v) ->
                val x = xOf(idx)
                if (x <= clipWidth + dotRadius) {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(x, yOf(v)),
                    )
                }
            }
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

@Composable
private fun InsightBarRow(
    label: String,
    days: Int,
    badness: Float,
    barFraction: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${days}日  不調 %.1f".format(badness),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(6.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .height(12.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        RoundedCornerShape(6.dp),
                    ),
            )
        }
    }
}

@Composable
private fun CorrelationInsightCard(
    insight: CorrelationInsight?,
) {
    val lowBarFraction by animateFloatAsState(
        targetValue = if (insight != null) (insight.lowPressureAvgBadness / 5f).coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "low_bar",
    )
    val highBarFraction by animateFloatAsState(
        targetValue = if (insight != null) (insight.highPressureAvgBadness / 5f).coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "high_bar",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "気圧 × 体調の相関",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (insight == null) {
                Text(
                    text = "気圧と体調の両方が記録された日が増えると、ここに傾向が現れます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.6,
                )
            } else {
                // 低気圧 vs 高気圧 の不調スコアバー比較
                InsightBarRow(
                    label = "低気圧  〜 ${insight.threshold.toInt()} hPa",
                    days = insight.lowPressureDays,
                    badness = insight.lowPressureAvgBadness,
                    barFraction = lowBarFraction,
                )
                InsightBarRow(
                    label = "高気圧  ${insight.threshold.toInt()} hPa 〜",
                    days = insight.highPressureDays,
                    badness = insight.highPressureAvgBadness,
                    barFraction = highBarFraction,
                )

                // 傾向サマリー
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ) {
                    Text(
                        text = insight.summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.6,
                    )
                }

                Text(
                    text = "${insight.totalPoints}日分のデータをもとに集計",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
