package com.cellomsai.steplog.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellomsai.steplog.ui.theme.ConditionColors
import com.cellomsai.steplog.ui.theme.StepLogTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val WeekDayLabels = DayOfWeek.entries.let { days ->
    // Start from Sunday
    listOf(DayOfWeek.SUNDAY) + days.filter { it != DayOfWeek.SUNDAY }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onDayClick: (String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = { viewModel.goToPreviousMonth() }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "前の月")
                        }
                        Text(
                            text = "%d年%d月".format(
                                uiState.displayMonth.year,
                                uiState.displayMonth.monthValue
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { viewModel.goToNextMonth() }) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "次の月")
                        }
                    }
                },
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
                .padding(horizontal = 8.dp),
        ) {
            // Weekday header
            Row(modifier = Modifier.fillMaxWidth()) {
                WeekDayLabels.forEach { dow ->
                    Text(
                        text = dow.getDisplayName(TextStyle.SHORT, Locale.JAPANESE),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Calendar grid
            val firstDay = uiState.displayMonth
            val firstDayOfWeek = firstDay.dayOfWeek.let { dow ->
                if (dow == DayOfWeek.SUNDAY) 0 else dow.value
            }
            val daysInMonth = firstDay.lengthOfMonth()
            val today = LocalDate.now()
            val cells = firstDayOfWeek + daysInMonth

            val rows = (cells + 6) / 7
            var dayCounter = 1

            repeat(rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellIndex = it * 7 + col
                        if (cellIndex < firstDayOfWeek || dayCounter > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = firstDay.withDayOfMonth(dayCounter)
                            val record = uiState.records[date.toString()]
                            val isToday = date == today
                            CalendarCell(
                                day = dayCounter,
                                steps = record?.steps,
                                conditionLevel = record?.dizzinessLevel,
                                isToday = isToday,
                                isFuture = date.isAfter(today),
                                onClick = { onDayClick(date.toString()) },
                                modifier = Modifier.weight(1f),
                            )
                            dayCounter++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    day: Int,
    steps: Int?,
    conditionLevel: Int?,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .then(
                if (isToday) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.small
                ) else Modifier
            )
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = !isFuture, onClick = onClick)
            .padding(4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurface,
            )

            steps?.takeIf { it > 0 }?.let {
                Text(
                    text = if (it >= 1000) "${it / 1000}k" else it.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            conditionLevel?.let { level ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ConditionColors.getOrElse(level) { Color.Transparent }),
                )
            } ?: Box(modifier = Modifier.size(6.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarScreenPreview() {
    StepLogTheme {
        Text("Calendar Preview")
    }
}
