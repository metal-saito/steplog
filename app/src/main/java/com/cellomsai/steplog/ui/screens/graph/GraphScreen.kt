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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellomsai.steplog.ui.theme.ConditionColors
import java.text.NumberFormat

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
